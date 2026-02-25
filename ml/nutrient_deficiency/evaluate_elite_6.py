import argparse
import json
import time
from pathlib import Path
import torch
import torch.nn as nn
from torch.utils.data import DataLoader
from torchvision import datasets
import timm
from timm.data import create_transform, resolve_model_data_config
from timm.utils.model_ema import ModelEmaV2
import numpy as np
import matplotlib.pyplot as plt
from sklearn.metrics import classification_report, confusion_matrix
import seaborn as sns

# ... (imports)
from torchvision import transforms

# ... (imports)

# ==========================================
# HELPERS
# ==========================================
class TenCropStackNormalize:
    def __init__(self, mean, std):
        self.norm = transforms.Compose([
            transforms.ToTensor(),
            transforms.Normalize(mean=mean, std=std)
        ])
    def __call__(self, crops):
        return torch.stack([self.norm(crop) for crop in crops])

# ==========================================
# CONFIG & ARGS
# ==========================================
def parse_args():
# ... (rest of parse_args)
    parser = argparse.ArgumentParser(description="Evaluate Elite 6 Model")
    parser.add_argument("--checkpoint", type=str, required=True, help="Path to best.pt or last.pt")
    parser.add_argument("--data-dir", type=str, default=r"D:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\nutrient_deficiency\dataset_v3_elite6_alltrain", help="Dataset root dir")
    parser.add_argument("--weights", type=str, choices=["ema", "raw"], default="ema", help="Which weights to use (default: ema)")
    parser.add_argument("--tta", action="store_true", help="Enable Test Time Augmentation (FiveCrop + Flip)")
    parser.add_argument("--img-size", type=int, default=384, help="Image size (default 384)")
    return parser.parse_args()

DEFAULT_CLASSES = ["Calcium", "Healthy", "Nitrogen", "Phosphorus", "Potassium", "Sulphur"]
BATCH_SIZE = 16 # Lower batch size for TTA (since it expands x10)
WORKERS = 4

def get_device():
    return torch.device("cuda" if torch.cuda.is_available() else "cpu")

def load_checkpoint(ckpt_path):
    path = Path(ckpt_path)
    if not path.exists():
        raise FileNotFoundError(f"Checkpoint not found: {path}")
    
    print(f"Loading checkpoint: {path}")
    # Using weights_only=False because we load full training state dicts
    ckpt = torch.load(path, map_location=get_device())
    return ckpt

def evaluate_model():
    device = get_device()
    print(f"Device: {device}")

    # 1. Load Config & Checkpoint
    args = parse_args()
    ckpt = load_checkpoint(args.checkpoint)
    
    # Extract config from checkpoint if available, else assume defaults
    cfg_dict = ckpt.get("cfg", {})
    model_name = cfg_dict.get("model_name", "convnextv2_base.fcmae_ft_in22k_in1k_384") 
    num_classes = len(DEFAULT_CLASSES)
    
    print(f"Model ID: {model_name}")
    print(f"Best Val Acc (from training): {ckpt.get('best_val_acc', '?')}")
    print(f"Epoch: {ckpt.get('epoch', '?')}")

    # 2. Rebuild Model
    model = timm.create_model(model_name, pretrained=False, num_classes=num_classes)
    model.to(device)
    
    # Load Weights
    # If user wants EMA and EMA is in checkpoint, load it
    if args.weights == "ema" and "ema" in ckpt and ckpt["ema"]:
        print("Loading EMA weights...")
        model.load_state_dict(ckpt["ema"])
    elif args.weights == "raw" or "model" in ckpt:
        print(f"Loading {args.weights.upper()} (Standard) weights...")
        model.load_state_dict(ckpt["model"])
    else:
        # Fallback if requested EMA but not found
        print("Warning: EMA weights requested but not found. Loading standard model weights...")
        model.load_state_dict(ckpt["model"])
        
    model.eval()

    # 3. Data Loading
    print("Preparing Test Data...")
    
    # TTA Transform vs Standard Transform
    if args.tta:
        print("🚀 TTA ENABLED: Using FiveCrop + HorizontalFlip (10x Inference)")
        # We need custom transform: Resize -> TenCrop -> ToTensor -> Normalize
        # Get normalization stats from model
        config = resolve_model_data_config(model)
        mean = config['mean']
        std = config['std']
        
        # We need to resize slightly larger than crops (standard practice is +32 or ratio)
        # For 384, we typically resize to 384/0.875 = 438 or just 420
        # Let's use simple logic: if img=384, resize=384+32=416, crop=384
        crop_size = args.img_size
        resize_size = int(crop_size / 0.875) 
        
        transform = transforms.Compose([
            transforms.Resize(resize_size),
            transforms.TenCrop(crop_size), # Returns tuple of 10 tensors
            TenCropStackNormalize(mean=mean, std=std)
        ])
    else:
        config = resolve_model_data_config(model)
        # Override input size if passed explicitly (critical for 384px)
        if hasattr(args, 'img_size'):
             config['input_size'] = (3, args.img_size, args.img_size)
             
        transform = create_transform(**config, is_training=False)
    
    test_dir = Path(args.data_dir) / "test"
    test_dataset = datasets.ImageFolder(str(test_dir), transform=transform)
    
    if test_dataset.classes != DEFAULT_CLASSES:
        print("⚠️ Warning: Dataset classes do not match default expectation!")
        print(f"Expected: {DEFAULT_CLASSES}")
        print(f"Found:    {test_dataset.classes}")
        
    test_loader = DataLoader(
        test_dataset, 
        batch_size=BATCH_SIZE if not args.tta else 4, # Force small batch 4 for TTA (4*10=40 is safe)
        shuffle=False, 
        num_workers=WORKERS,
        pin_memory=(device.type == "cuda")
    )

    # 4. Inference
    print(f"Running Inference on {len(test_dataset)} images...")
    
    y_true = []
    y_pred = []
    loss_sum = 0.0
    criterion = nn.CrossEntropyLoss()
    
    with torch.no_grad():
        for inputs, labels in test_loader:
            labels = labels.to(device)
            
            with torch.cuda.amp.autocast(enabled=(device.type == "cuda")):
                if args.tta:
                    # Input is [B, 10, C, H, W]
                    inputs = inputs.to(device)
                    bs, ncrops, c, h, w = inputs.size()
                    
                    # Fuse batch and crops: [B*10, C, H, W]
                    inputs_fused = inputs.view(-1, c, h, w)
                    
                    # Run inference
                    outputs_fused = model(inputs_fused) # [B*10, NumClasses]
                    
                    # Reshape back: [B, 10, NumClasses]
                    outputs_unfused = outputs_fused.view(bs, ncrops, -1)
                    
                    # Average predictions (mean of logits or softmax? Mean of softmax usually slightly better for ensembles)
                    # Let's use mean of logits for stability with CrossEntropyLoss check
                    outputs = outputs_unfused.mean(dim=1)
                    
                else:
                    inputs = inputs.to(device)
                    outputs = model(inputs)
                
                loss = criterion(outputs, labels)
            
            loss_sum += loss.item() * inputs.size(0)
            
            _, preds = torch.max(outputs, 1)
            
            y_true.extend(labels.cpu().numpy())
            y_pred.extend(preds.cpu().numpy())

    # 5. Metrics
    test_loss = loss_sum / len(test_dataset)
    test_acc = np.mean(np.array(y_true) == np.array(y_pred))

    print("\n" + "="*50)
    print(f"RESULTS FOR: Elite 6 Model ({'TTA' if args.tta else 'Single'})")
    print(f"Source: {args.checkpoint}")
    print("="*50)
    print(f"Test Accuracy : {test_acc:.4f} ({test_acc*100:.2f}%)")
    print(f"Test Loss     : {test_loss:.4f}")
    print("-" * 50)
    
    # Classification Report
    report = classification_report(y_true, y_pred, target_names=DEFAULT_CLASSES, digits=4)
    print("\nClassification Report:\n")
    print(report)
    
    # Confusion Matrix
    cm = confusion_matrix(y_true, y_pred)
    
    # Plotting
    plt.figure(figsize=(10, 8))
    sns.heatmap(cm, annot=True, fmt='d', cmap='Blues', 
                xticklabels=DEFAULT_CLASSES, yticklabels=DEFAULT_CLASSES)
    plt.xlabel('Predicted')
    plt.ylabel('True')
    title = f'Confusion Matrix ({"TTA" if args.tta else "Single"})\nAcc: {test_acc:.4f}'
    plt.title(title)
    
    out_path = Path(args.checkpoint).parent / f"eval_confusion_matrix_{'tta' if args.tta else 'single'}.png"
    plt.tight_layout()
    plt.savefig(out_path)
    print(f"\nConfusion matrix saved to: {out_path}")
    
    # Save text report
    txt_path = Path(args.checkpoint).parent / f"eval_report_{'tta' if args.tta else 'single'}.txt"
    with open(txt_path, "w") as f:
        f.write(f"Evaluating: {args.checkpoint}\n")
        f.write(f"Mode: {'TTA (10-crop)' if args.tta else 'Single View'}\n")
        f.write(f"Test Accuracy: {test_acc:.4f}\n")
        f.write(f"Test Loss: {test_loss:.4f}\n\n")
        f.write(report)
    print(f"Report saved to: {txt_path}")

if __name__ == "__main__":
    evaluate_model()

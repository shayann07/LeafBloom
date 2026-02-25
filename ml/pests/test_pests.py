import torch
import torch.nn as nn
from torchvision import transforms, models
from PIL import Image
import os

# Config
MODEL_PATH = "pest_id_model.pth"
DATA_DIR = r"d:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\pests\dataset\test"
# Alphabetical order from training script (12 classes)
CLASSES = ['ants', 'bees', 'beetle', 'catterpillar', 'earthworms', 'earwig', 'grasshopper', 'moth', 'slug', 'snail', 'wasp', 'weevil']
CONFIDENCE_THRESHOLD = 0.60 # Threshold to reject unknown pests
DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")

def load_model():
    print(f"Loading {MODEL_PATH} (ResNet18)...")
    # Using default weights structure to match training
    model = models.resnet18(weights=None) 
    num_ftrs = model.fc.in_features
    # Match the Sequential structure from training
    model.fc = nn.Sequential(
        nn.Dropout(0.3),
        nn.Linear(num_ftrs, len(CLASSES))
    )
    
    if os.path.exists(MODEL_PATH):
        model.load_state_dict(torch.load(MODEL_PATH, map_location=DEVICE))
    else:
        print(f"❌ Model not found: {MODEL_PATH}")
        return None

    model.to(DEVICE)
    model.eval()
    return model

def get_random_test_images(num_per_class=1):
    import random
    image_paths = []
    
    # 1. Select random KNOWN pests
    for cls in CLASSES:
        cls_dir = os.path.join(DATA_DIR, cls)
        if not os.path.exists(cls_dir): continue
        files = [f for f in os.listdir(cls_dir) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
        if files:
            image_paths.extend([os.path.join(cls_dir, f) for f in random.sample(files, min(len(files), 2))])
            
    # 2. Select random UNKNOWNS for rejection testing
    unknown_dir = r"d:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\pests\dataset\unknown_calibration_test\unknown"
    if os.path.exists(unknown_dir):
        files = [f for f in os.listdir(unknown_dir) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
        if files:
            image_paths.extend([os.path.join(unknown_dir, f) for f in random.sample(files, min(len(files), 10))])

    random.shuffle(image_paths)
    return image_paths

def predict_custom_images():
    model = load_model()
    if model is None: return

    # Match Validation Transform (Resize 256 -> CenterCrop 224)
    transform = transforms.Compose([
        transforms.Resize(256),
        transforms.CenterCrop(224),
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
    ])

    # Get random images from dataset
    image_paths = get_random_test_images()
    if not image_paths:
        print("❌ No images found in test directories.")
        return

    print(f"\nStarting Predictions ({len(image_paths)} images):\n" + "-"*30)

    for i, img_path in enumerate(image_paths):
        try:
            # Load & Preprocess
            img = Image.open(img_path)
            img_t = transform(img).unsqueeze(0).to(DEVICE)

            # Predict
            with torch.no_grad():
                outputs = model(img_t)
                probs = torch.nn.functional.softmax(outputs, dim=1)
                conf, pred_idx = torch.max(probs, 1)
                
                confidence = conf.item() * 100
                pred_label = CLASSES[pred_idx.item()]
                
                # CONFIDENCE THRESHOLD LOGIC
                THRESHOLD = 60.0 # If less than 60% sure, it's Unknown
                
                final_label = pred_label
                if confidence < THRESHOLD:
                    final_label = "UNKNOWN (Low Confidence)"
                    
            # True Label
            true_label = os.path.basename(os.path.dirname(img_path))
            
            # Console Output
            print(f"File: {os.path.basename(img_path)}")
            print(f"True Label: {true_label.upper()}")
            print(f"Raw Prediction: {pred_label.upper()} ({confidence:.2f}%)")
            print(f"Final Result: {final_label.upper()}")
            
            # Top 3 Probably
            top3_prob, top3_idx = torch.topk(probs, 3)
            print("Top 3:")
            for j in range(3):
                idx = top3_idx[0][j].item()
                prob = top3_prob[0][j].item()
                print(f"  - {CLASSES[idx]}: {prob*100:.2f}%")
            print("-" * 30)

        except Exception as e:
            print(f"Error processing {img_path}: {e}")

if __name__ == "__main__":
    predict_custom_images()

import torch
from torchvision import transforms
from PIL import Image
import os
import glob
from torch.jit._serialization import load

# Config
MODEL_PATH = "pest_id_model.ptl"
DATA_DIR = r"d:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\pests\dataset\test"
# Alphabetical order from training script (12 classes)
CLASSES = ['ants', 'bees', 'beetle', 'catterpillar', 'earthworms', 'earwig', 'grasshopper', 'moth', 'slug', 'snail', 'wasp', 'weevil']

def load_lite_model():
    print(f"Loading Mobile Lite Model from {MODEL_PATH}...")
    try:
        model = torch.jit.load(MODEL_PATH) 
        model.eval()
        return model
    except Exception as e:
        print(f"Failed to load mobile model: {e}")
        return None

def predict_test_dataset():
    model = load_lite_model()
    if model is None: return

    # Match Validation Transform (Resize 256 -> CenterCrop 224)
    transform = transforms.Compose([
        transforms.Resize(256),
        transforms.CenterCrop(224),
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
    ])

    print("Starting Predictions on Pest Test Dataset (converted model):")
    print("-" * 30)

    total_images = 0
    correct_predictions = 0

    for cls_idx, cls_name in enumerate(CLASSES):
        cls_dir = os.path.join(DATA_DIR, cls_name)
        if not os.path.isdir(cls_dir):
            print(f"Directory not found: {cls_dir}")
            continue

        images = glob.glob(os.path.join(cls_dir, "*.[jJ][pP][gG]")) + glob.glob(os.path.join(cls_dir, "*.[pP][nN][gG]"))
        
        for img_path in images:
            total_images += 1
            try:
                # Load & Preprocess
                img = Image.open(img_path).convert('RGB')
                img_t = transform(img).unsqueeze(0) # Keep on CPU for lite model evaluation

                # Predict
                with torch.no_grad():
                    # Expected to be wrapper from convert script, so returning probabilities directly
                    outputs = model(img_t)
                    probs = outputs[0] if isinstance(outputs, tuple) else outputs
                    
                    conf, pred_idx = torch.max(probs, 1)
                    pred_label = CLASSES[pred_idx.item()]
                    
                if pred_label == cls_name:
                    correct_predictions += 1
                else:
                    print(f"Incorrect: {os.path.basename(img_path)} (True: {cls_name}, Pred: {pred_label})")

            except Exception as e:
                print(f"Error processing {img_path}: {e}")

    print("-" * 30)
    print(f"Total Images Evaluated: {total_images}")
    if total_images > 0:
        accuracy = (correct_predictions / total_images) * 100
        print(f"Accuracy: {accuracy:.2f}% ({correct_predictions}/{total_images})")

if __name__ == "__main__":
    predict_test_dataset()

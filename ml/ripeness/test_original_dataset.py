import torch
import torch.nn as nn
from torchvision import transforms, models
from PIL import Image
import os
import glob

# Config
MODEL_PATH = "ripeness_3class.pth"
CLASSES = ['Ripe', 'Unknown', 'Unripe'] # 0=Ripe, 1=Unknown, 2=Unripe
DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")
TEST_DIR = r"D:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\ripeness\dataset\Test"

def load_model():
    print(f"Loading {MODEL_PATH} (ResNet18)...")
    model = models.resnet18(weights=None)
    num_ftrs = model.fc.in_features
    model.fc = nn.Linear(num_ftrs, 3) # 3 Classes
    
    if os.path.exists(MODEL_PATH):
        model.load_state_dict(torch.load(MODEL_PATH, map_location=DEVICE))
    else:
        print(f"Model not found: {MODEL_PATH}")
        return None

    model.to(DEVICE)
    model.eval()
    return model

def predict_test_dataset():
    model = load_model()
    if model is None: return

    transform = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
    ])

    print("Starting Predictions on Test Dataset:")
    print("-" * 30)

    total_images = 0
    correct_predictions = 0

    for cls_idx, cls_name in enumerate(CLASSES):
        cls_dir = os.path.join(TEST_DIR, cls_name)
        if not os.path.isdir(cls_dir):
            print(f"Directory not found: {cls_dir}")
            continue

        images = glob.glob(os.path.join(cls_dir, "*.[jJ][pP][gG]")) + glob.glob(os.path.join(cls_dir, "*.[pP][nN][gG]"))
        
        for img_path in images:
            total_images += 1
            try:
                # Load & Preprocess
                img = Image.open(img_path).convert('RGB')
                img_t = transform(img).unsqueeze(0).to(DEVICE)

                # Predict
                with torch.no_grad():
                    outputs = model(img_t)
                    probs = torch.nn.functional.softmax(outputs, dim=1)
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

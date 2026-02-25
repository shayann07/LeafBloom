import torch
import torch.nn as nn
from torchvision import transforms, models
from PIL import Image
import os
import matplotlib.pyplot as plt

# Config
MODEL_PATH = "ripeness_binary.pth"
CLASSES = ['Ripe', 'Unripe'] # Alphabetical order from ImageFolder
DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")

# Custom Images
IMAGE_PATHS = [
    r"C:\Users\shaya\OneDrive\Desktop\New folder\ripe.jpg",
    r"C:\Users\shaya\OneDrive\Desktop\New folder\unripe.jpg",
    r"C:\Users\shaya\OneDrive\Desktop\New folder\semiripe.jpg",
    r"C:\Users\shaya\OneDrive\Desktop\New folder\tomato_leaf.jpg",
    r"C:\Users\shaya\OneDrive\Desktop\New folder\unknown.jpg"
]

def load_model():
    print(f"Loading {MODEL_PATH} (ResNet18)...")
    model = models.resnet18(pretrained=False)
    num_ftrs = model.fc.in_features
    model.fc = nn.Linear(num_ftrs, 2)
    model.load_state_dict(torch.load(MODEL_PATH, map_location=DEVICE))
    model.to(DEVICE)
    model.eval()
    return model

def predict_custom_images():
    if not os.path.exists(MODEL_PATH):
        print(f"❌ Model not found: {MODEL_PATH}")
        return

    model = load_model()

    transform = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
    ])

    print("\nStarting Predictions:\n" + "-"*30)

    for i, img_path in enumerate(IMAGE_PATHS):
        if not os.path.exists(img_path):
            print(f"❌ File not found: {img_path}")
            continue

        try:
            # Load & Preprocess
            img = Image.open(img_path)
            img_t = transform(img).unsqueeze(0).to(DEVICE)

            # Predict
            with torch.no_grad():
                outputs = model(img_t)
                probs = torch.nn.functional.softmax(outputs, dim=1)
                conf, pred_idx = torch.max(probs, 1)
                pred_label = CLASSES[pred_idx.item()]
                confidence = conf.item() * 100

            # Console Output
            print(f"File: {os.path.basename(img_path)}")
            print(f"Prediction: {pred_label.upper()} ({confidence:.2f}%)")
            print("Probabilities:")
            for j, cls in enumerate(CLASSES):
                print(f"  - {cls}: {probs[0][j].item()*100:.2f}%")
            print("-" * 30)

        except Exception as e:
            print(f"Error processing {img_path}: {e}")

if __name__ == "__main__":
    predict_custom_images()

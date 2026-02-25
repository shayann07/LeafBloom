import torch
from torchvision import transforms
from PIL import Image
import os

# Config
MODEL_PATH = "ripeness_3class.ptl"
CLASSES = ['Ripe', 'Unknown', 'Unripe'] # 0=Ripe, 1=Unknown, 2=Unripe

# Custom Images
IMAGE_PATHS = [
    r"C:\Users\shaya\OneDrive\Desktop\New folder\ripe.jpg",
    r"C:\Users\shaya\OneDrive\Desktop\New folder\unripe.jpg",
    r"C:\Users\shaya\OneDrive\Desktop\New folder\semiripe.jpg",
    r"C:\Users\shaya\OneDrive\Desktop\New folder\tomato_leaf.jpg",
    r"C:\Users\shaya\OneDrive\Desktop\New folder\unknown.jpg"
]

def main():
    if not os.path.exists(MODEL_PATH):
        print(f"❌ Error: Model file not found: {MODEL_PATH}")
        return

    print(f"Loading {MODEL_PATH} (TorchScript)...")
    try:
        model = torch.jit.load(MODEL_PATH)
        model.eval()
    except Exception as e:
        print(f"❌ Failed to load model: {e}")
        return

    transform = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
    ])

    print("\nStarting Predictions (Mobile Model):\n" + "-"*30)

    for img_path in IMAGE_PATHS:
        if not os.path.exists(img_path):
            print(f"❌ File not found: {img_path}")
            continue

        try:
            # Load & Preprocess
            img = Image.open(img_path)
            img_t = transform(img).unsqueeze(0) # Input is (1, 3, 224, 224)

            # Predict
            with torch.no_grad():
                # mobile model already has Softmax inside!
                probs = model(img_t) 
                
                # Get max probability
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
    main()

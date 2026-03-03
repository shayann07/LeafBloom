import torch
from torchvision import transforms
from PIL import Image
import os
import glob
from torch.jit._serialization import load

# Config
MODEL_PATH = "ripeness_3class.ptl"
CLASSES = ['Ripe', 'Unknown', 'Unripe'] # 0=Ripe, 1=Unknown, 2=Unripe
TEST_DIR = r"D:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\ripeness\dataset\Test"

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

    transform = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
    ])

    print("\nStarting Predictions on Test Dataset (converted model):")
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
                img_t = transform(img).unsqueeze(0) # Keep on CPU for lite model evaluation

                # Predict
                with torch.no_grad():
                    # For Lite/Mobile Optimized models, they usually don't need softmax if trained properly and wrapper handles it,
                    # but since the wrapper has a softmax, the output contains probabilities.
                    outputs = model(img_t)
                    
                    # Assuming it returns a tuple of probabilities or a tensor of probabilities directly
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

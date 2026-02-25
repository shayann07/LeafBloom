import torch
import torchvision.transforms as transforms
from PIL import Image
import os
import random

# Config
MODEL_PATH = "pest_id_model.ptl"
DATA_DIR = r"d:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\pests\dataset\test"
# 12 Classes
CLASSES = ['ants', 'bees', 'beetle', 'catterpillar', 'earthworms', 'earwig', 'grasshopper', 'moth', 'slug', 'snail', 'wasp', 'weevil']

def load_mobile_model():
    print(f"Loading {MODEL_PATH}...")
    try:
        model = torch.jit.load(MODEL_PATH)
        model.eval()
        return model
    except Exception as e:
        print(f"❌ Error loading model: {e}")
        return None

def test_mobile_model():
    model = load_mobile_model()
    if model is None: return

    # Same transforms as validation/app
    transform = transforms.Compose([
        transforms.Resize(256),
        transforms.CenterCrop(224),
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
    ])

    print(f"\n{'='*50}")
    print(f"TESTING MOBILE MODEL (.ptl) ON 1 IMAGE PER CLASS")
    print(f"{'='*50}\n")

    correct = 0
    total = 0

    for cls in CLASSES:
        cls_dir = os.path.join(DATA_DIR, cls)
        if not os.path.exists(cls_dir):
            print(f"⚠️ Directory not found for {cls}")
            continue

        files = [f for f in os.listdir(cls_dir) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
        if not files:
            print(f"⚠️ No images found for {cls}")
            continue

        # Pick 1 random image
        img_name = random.choice(files)
        img_path = os.path.join(cls_dir, img_name)
        
        try:
            # Preprocess
            img = Image.open(img_path).convert('RGB')
            input_tensor = transform(img).unsqueeze(0) # Add batch dim

            # Inference
            with torch.no_grad():
                outputs = model(input_tensor)
                # Model already has Softmax baked in? 
                # Our convert script wrapped it in SoftmaxWrapper, so output is PROBABILITIES.
                probs = outputs[0]
                conf, pred_idx = torch.max(probs, 0)
                
            pred_label = CLASSES[pred_idx.item()]
            confidence = conf.item() * 100
            
            is_correct = (pred_label == cls)
            if is_correct: correct += 1
            total += 1
            
            status = "✅" if is_correct else "❌"
            
            print(f"File: {cls}/{img_name}")
            print(f"Prediction: {pred_label.upper()} ({confidence:.2f}%) {status}")
            if not is_correct:
                print(f"   (True Label: {cls.upper()})")
            print("-" * 30)

        except Exception as e:
            print(f"❌ Error processing {img_name}: {e}")

    print(f"\nFinal Result: {correct}/{total} Correct ({(correct/total)*100:.2f}%)")

    # --- Test Unknowns ---
    print(f"\n{'='*50}")
    print(f"TESTING MOBILE MODEL (.ptl) ON 3 UNKNOWN IMAGES")
    print(f"{'='*50}\n")
    
    # Correct path: images are directly in this folder
    unknown_dir = r"d:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\pests\dataset\unknown_calibration_test"
    if os.path.exists(unknown_dir):
        files = [f for f in os.listdir(unknown_dir) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
        if files:
            selected = random.sample(files, min(len(files), 3))
            for f in selected:
                img_path = os.path.join(unknown_dir, f)
                try:
                    img = Image.open(img_path).convert('RGB')
                    input_tensor = transform(img).unsqueeze(0)

                    with torch.no_grad():
                        outputs = model(input_tensor)
                        probs = outputs[0]
                        conf, pred_idx = torch.max(probs, 0)
                    
                    pred_label = CLASSES[pred_idx.item()]
                    confidence = conf.item() * 100
                    
                    print(f"File: unknown/{f}")
                    print(f"Prediction: {pred_label.upper()} ({confidence:.2f}%)")
                    
                    if confidence < 60.0:
                        print("Result: ✅ Correctly Rejected (Low Confidence)")
                    else:
                        print("Result: ⚠️ False Acceptance (High Confidence)")
                    print("-" * 30)
                except Exception as e:
                    print(f"Error: {e}")
        else:
            print("No unknown images found.")
    else:
        print(f"Unknown directory not found: {unknown_dir}")

if __name__ == "__main__":
    test_mobile_model()

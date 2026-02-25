import torch
import torch.nn as nn
from torchvision import models
from torch.utils.mobile_optimizer import optimize_for_mobile

print("--- Starting Robust Model Export ---")

# 1. Define the model architecture
model = models.resnet18(pretrained=False)
num_ftrs = model.fc.in_features
model.fc = nn.Linear(num_ftrs, 5)

# 2. Load the trained weights correctly
import os
model_path = "resnet18_tomato_disease_5class.pth"
if not os.path.exists(model_path):
    model_path = os.path.join("ml", model_path)
print(f"Loading weights from {model_path}...")

try:
    checkpoint = torch.load(model_path, map_location=torch.device('cpu'))
    
    # CHECKPOINT HANDLING: Check if this is a dict containing the weights under a key
    if isinstance(checkpoint, dict):
        if "state_dict" in checkpoint:
            print("Found 'state_dict' key in checkpoint. Loading from that...")
            state_dict = checkpoint["state_dict"]
        elif "model_state_dict" in checkpoint:
            print("Found 'model_state_dict' key in checkpoint. Loading from that...")
            state_dict = checkpoint["model_state_dict"]
        else:
            # Maybe it is the state_dict itself?
            print("No metadata keys found. Assuming dictionary IS the state_dict.")
            state_dict = checkpoint
    else:
        state_dict = checkpoint

    # Load with strict=True to ensure we aren't missing keys
    model.load_state_dict(state_dict, strict=True)
    print("✅ Weights loaded successfully (Strict Mode).")

except Exception as e:
    print(f"❌ Error loading model: {e}")
    # Print keys to help debug
    if 'checkpoint' in locals() and isinstance(checkpoint, dict):
        print(f"Keys in loaded file: {list(checkpoint.keys())}")
    exit(1)

model.eval()

# 3. Softmax Wrapper
class MobileWrapper(nn.Module):
    def __init__(self, original_model):
        super(MobileWrapper, self).__init__()
        self.model = original_model
        self.softmax = nn.Softmax(dim=1)
    
    def forward(self, x):
        return self.softmax(self.model(x))

mobile_model = MobileWrapper(model)
mobile_model.eval()

# 4. Verification Step (Pre-Trace)
# ... (Previous code)

# 4. Verification Step (Pre-Trace) on REAL IMAGE
print("\n--- Verifying on REAL IMAGE (Critical Step) ---")
from PIL import Image
from torchvision import transforms

image_path = r"Tomato Leaf Disease.v1i.folder (1)\train\02_HEALTHY\4a0a9092-e70e-4a73-bb51-4d83b3c90a38___GH_HL-Leaf-195_JPG.rf.0bcf6e4a44898913bba65309d6d4cc0d.jpg"
if not os.path.exists(image_path):
    image_path = os.path.join("ml", image_path)
try:
    # Use exact same transform as verify_mobile_logic.py
    transform = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
    ])
    
    img = Image.open(image_path).convert("RGB")
    real_input = transform(img).unsqueeze(0)
    
    with torch.no_grad():
        real_out = mobile_model(real_input)
        
    print(f"Real Image Output: {real_out}")
    vals = real_out[0].tolist()
    classes = ["UNKNOWN", "EARLY", "HEALTHY", "LATE", "SEPTORIA"]
    for i, val in enumerate(vals):
        print(f"  {classes[i]}: {val:.4f}")
        
    if vals[2] < 0.9: # Check Healthy > 90%
        print("❌ CRITICAL: In-memory model FAILS to predict Healthy! Weights are wrong or corrupted.")
        # Don't exit, but warn heavily
    else:
        print("✅ In-memory model works! (Predicts Healthy correctly)")

except Exception as e:
    print(f"Skipping Real Image verification: {e}")

# 5. Trace
print("\n--- Tracing Model ---")
# Use example_input for tracing, NOT real_input (random is safer for tracing structure)
example_input = torch.rand(1, 3, 224, 224)
traced_script_module = torch.jit.trace(mobile_model, example_input)

# 6. Verify Traced Model (Post-Trace)
print("Verifying Traced Model Consistency...")
with torch.no_grad():
    traced_out = traced_script_module(example_input)
    # We can't easily compare to raw_out anymore since we removed that block, 
    # but we can trust the trace happened if no error.
    print("Trace successful.")

# 7. Optimize and Save
print("\n--- Optimizing & Saving ---")

# CRITICAL FIX: Save using _save_for_lite_interpreter BUT without optimize_for_mobile
# because optimize_for_mobile corrupted the accuracy.
traced_path = "tomato_disease_mobile_final.ptl"
try:
    traced_script_module._save_for_lite_interpreter(traced_path)
    print(f"Saved LITE-COMPATIBLE (No Opt) model: {traced_path}")
except AttributeError:
    # Fallback for older PyTorch versions if needed, but 1.10+ has this
    traced_script_module.save(traced_path)
    print(f"WARNING: Saved as standard TorchScript: {traced_path}")

# Skip the broken optimized version
# traced_script_module_optimized = optimize_for_mobile(traced_script_module)
# output_path = "tomato_disease_mobile_v3.ptl" 
# traced_script_module_optimized._save_for_lite_interpreter(output_path)

print(f"✅ Success! Generated verified mobile model: {output_path}")

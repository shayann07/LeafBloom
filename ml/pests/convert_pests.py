import torch
import torch.nn as nn
from torchvision import models
from torch.utils.mobile_optimizer import optimize_for_mobile
import os

# Config
MODEL_PATH = "pest_id_model.pth"
SAVE_PATH = "pest_id_model.ptl"
# Alphabetical order from training script
# Alphabetical order from training script (12 classes)
CLASSES = ['ants', 'bees', 'beetle', 'catterpillar', 'earthworms', 'earwig', 'grasshopper', 'moth', 'slug', 'snail', 'wasp', 'weevil']

class SoftmaxWrapper(nn.Module):
    def __init__(self, model):
        super(SoftmaxWrapper, self).__init__()
        self.model = model
        self.softmax = nn.Softmax(dim=1)

    def forward(self, x):
        logits = self.model(x)
        probs = self.softmax(logits)
        return probs

def main():
    if not os.path.exists(MODEL_PATH):
        print(f"❌ Error: Model file not found: {MODEL_PATH}")
        return

    print(f"Loading {MODEL_PATH}...")
    
    # Load original model (ResNet18)
    model = models.resnet18(weights=None)
    num_ftrs = model.fc.in_features
    # Match the Sequential structure from training
    model.fc = nn.Sequential(
        nn.Dropout(0.3),
        nn.Linear(num_ftrs, len(CLASSES))
    )
    
    # Load weights
    device = torch.device("cpu")
    model.load_state_dict(torch.load(MODEL_PATH, map_location=device))
    model.eval()

    # Wrap with Softmax
    print("Wrapping with Softmax...")
    probability_model = SoftmaxWrapper(model)
    probability_model.eval()

    # Verification Input
    print("Verifying correctness...")
    example_input = torch.randn(1, 3, 224, 224) 
    
    with torch.no_grad():
        out_orig = probability_model(example_input)
        print(f"Original PyTorch Output: {out_orig[0].tolist()}")

    # Trace
    print("Tracing...")
    traced_model = torch.jit.trace(probability_model, example_input)
    
    with torch.no_grad():
        out_traced = traced_model(example_input)
        print(f"Traced Model Output:   {out_traced[0].tolist()}")
        
    if not torch.allclose(out_orig, out_traced, atol=1e-5):
        print("❌ CRITICAL: Traced model output mismatches original!")
        return

    # Optimize
    print("Optimizing for mobile...")
    optimized_model = optimize_for_mobile(traced_model)
    
    with torch.no_grad():
        out_opt = optimized_model(example_input)
        print(f"Optimized Model Output:{out_opt[0].tolist()}")

    if not torch.allclose(out_orig, out_opt, atol=1e-5):
        print("⚠️ Warning: Optimized model deviates from original. Saving TRACED model instead.")
        traced_model.save(SAVE_PATH)
        print(f"Saved TRACED model to {SAVE_PATH}")
    else:
        optimized_model._save_for_lite_interpreter(SAVE_PATH)
        print(f"✅ Success! Saved OPTIMIZED mobile model to {SAVE_PATH}")

if __name__ == "__main__":
    main()

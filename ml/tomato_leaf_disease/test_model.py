import torch
import torch.nn as nn
import torchvision.transforms as transforms
from torchvision import models
from PIL import Image

# Set device (GPU if available, otherwise CPU)
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

# Define class labels (update with actual class names)
class_labels = ["00_UNKNOWN", "01_EARLY_BLIGHT", "02_HEALTHY", "03_LATE_BLIGHT", "04_SEPTORIA"]  # Ensure class names match training folder order

# Define image preprocessing (same as training)
transform = transforms.Compose([
    transforms.Resize((224, 224)),  # Resize image to match ResNet18 input size
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
])

# Load the trained model
model = models.resnet18(pretrained=False)
num_ftrs = model.fc.in_features
model.fc = nn.Linear(num_ftrs, len(class_labels))  #Adjust number of output classes

# Load model with correct device mapping
model.load_state_dict(torch.load("resnet18_tomato_disease_5class.pth", map_location=device))
model.to(device)
model.eval()  # Set model to evaluation mode

# Function to predict image class with confidence score
def predict_image(image_path):
    try:
        # Open image and apply transformations
        image = Image.open(image_path).convert("RGB")
        image = transform(image).unsqueeze(0).to(device)  # Add batch dimension

        # Run inference
        with torch.no_grad():
            outputs = model(image)  # Forward pass
            probabilities = torch.softmax(outputs, dim=1)  # Convert logits to probabilities
            confidence, predicted_idx = torch.max(probabilities, 1)  # Get class index & confidence
            predicted_class = class_labels[predicted_idx.item()]  # Get class label

        print(f"Predicted Class: {predicted_class} (Confidence: {confidence.item() * 100:.2f}%)")
        
        print("\n📊 Full Probabilities:")
        for i, prob in enumerate(probabilities[0]):
            print(f"  {class_labels[i]}: {prob.item() * 100:.2f}%")

        return predicted_class

    except Exception as e:
        print(f"Error processing image: {e}")
        return None

# Test the function

image_path = r"D:\Work\AndroidStudioProjects\FYP\Tomato Leaf Disease\Tomato Leaf Disease.v1i.folder (1)\train\04_SEPTORIA\f5f9f526-893f-467f-8345-75f707456b3a___Matt-S_CG-7511_JPG.rf.ff634a1b8610a29bec2454325cee2b3a.jpg"

predicted_class = predict_image(image_path)

import os
import torch
import torch.nn as nn
import torchvision.transforms as transforms
from torchvision import models
from PIL import Image

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
class_labels = ["Early Blight", "Healthy", "Late Blight", "Septoria"]

# The validation transform should not contain random augmentation like crops or jitter
# We just resize and normalize it.
transform = transforms.Compose([
    transforms.Resize((224, 224)), 
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
])

model = models.resnet18(weights=None)
num_ftrs = model.fc.in_features
model.fc = nn.Linear(num_ftrs, len(class_labels))

model.load_state_dict(torch.load("resnet18_tomato_disease_robust.pth", map_location=device))
model.to(device)
model.eval() 

def predict_folder(folder_path):
    print(f"Testing ROBUST Model on images in: {folder_path}\n" + "-"*50)
    
    if not os.path.exists(folder_path):
        print("Folder not found.")
        return
        
    for filename in os.listdir(folder_path):
        if filename.lower().endswith(('.png', '.jpg', '.jpeg')):
            image_path = os.path.join(folder_path, filename)
            try:
                image = Image.open(image_path).convert("RGB")
                image_tensor = transform(image).unsqueeze(0).to(device)
                
                with torch.no_grad():
                    outputs = model(image_tensor)
                    probabilities = torch.softmax(outputs, dim=1)
                    confidence, predicted_idx = torch.max(probabilities, 1)
                    predicted_class = class_labels[predicted_idx.item()]
                    
                print(f"File: {filename}")
                print(f"Prediction: {predicted_class} | Confidence: {confidence.item() * 100:.2f}%\n")
                
            except Exception as e:
                print(f"Error processing {filename}: {e}\n")

if __name__ == '__main__':
    target_folder = r"C:\Users\shaya\OneDrive\Desktop\New folder"
    predict_folder(target_folder)

import os
import torch
import torch.nn as nn
import torch.optim as optim
from torchvision import datasets, transforms, models
from torch.utils.data import DataLoader

# ----------------------------
# Device (GPU / CPU)
# ----------------------------
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print("Using device:", device)
if device.type == "cuda":
    print("CUDA:", torch.version.cuda)
    print("GPU:", torch.cuda.get_device_name(0))

# ----------------------------
# Paths (edit ONLY if needed)
# ----------------------------
TRAIN_DIR = r"Tomato Leaf Disease.v1i.folder (1)\train"
VAL_DIR   = r"Tomato Leaf Disease.v1i.folder (1)\valid"

if not os.path.isdir(TRAIN_DIR):
    raise FileNotFoundError(f"Train directory not found: {os.path.abspath(TRAIN_DIR)}")
if not os.path.isdir(VAL_DIR):
    raise FileNotFoundError(f"Valid directory not found: {os.path.abspath(VAL_DIR)}")

# ----------------------------
# Transforms
# ----------------------------
transform = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406],
                         std=[0.229, 0.224, 0.225])
])

# ----------------------------
# Datasets / Loaders
# ----------------------------
train_data = datasets.ImageFolder(root=TRAIN_DIR, transform=transform)
val_data   = datasets.ImageFolder(root=VAL_DIR, transform=transform)

print("Loaded classes:", train_data.classes)
print("Class -> index:", train_data.class_to_idx)
num_classes = len(train_data.classes)
print("Num classes:", num_classes)

# Safety: you want exactly 5 classes for UNKNOWN + 4 diseases
if num_classes != 5:
    raise ValueError(
        f"Expected 5 classes, but found {num_classes}. "
        f"Check your folder names and that each class folder contains images."
    )

# Workers: safe default for Windows
train_loader = DataLoader(train_data, batch_size=32, shuffle=True, num_workers=0, pin_memory=(device.type == "cuda"))
val_loader   = DataLoader(val_data, batch_size=32, shuffle=False, num_workers=0, pin_memory=(device.type == "cuda"))

# ----------------------------
# Model
# ----------------------------
# Keep your current call; warnings are fine.
model = models.resnet18(pretrained=True)

# Replace final layer
num_ftrs = model.fc.in_features
model.fc = nn.Linear(num_ftrs, num_classes)

model = model.to(device)

# ----------------------------
# Loss / Optimizer
# ----------------------------
criterion = nn.CrossEntropyLoss()
optimizer = optim.Adam(model.parameters(), lr=0.001)

# ----------------------------
# Training
# ----------------------------
epochs = 15
for epoch in range(epochs):
    model.train()
    running_loss = 0.0
    correct = 0
    total = 0

    for images, labels in train_loader:
        images = images.to(device, non_blocking=True)
        labels = labels.to(device, non_blocking=True)

        optimizer.zero_grad(set_to_none=True)
        outputs = model(images)
        loss = criterion(outputs, labels)
        loss.backward()
        optimizer.step()

        running_loss += loss.item()
        _, predicted = outputs.max(1)
        total += labels.size(0)
        correct += predicted.eq(labels).sum().item()

    train_accuracy = 100.0 * correct / total
    print(f"Epoch {epoch + 1}/{epochs}, Loss: {running_loss / len(train_loader):.4f}, Accuracy: {train_accuracy:.2f}%")

# ----------------------------
# Validation
# ----------------------------
model.eval()
correct = 0
total = 0
with torch.no_grad():
    for images, labels in val_loader:
        images = images.to(device, non_blocking=True)
        labels = labels.to(device, non_blocking=True)

        outputs = model(images)
        _, predicted = outputs.max(1)
        total += labels.size(0)
        correct += predicted.eq(labels).sum().item()

val_accuracy = 100.0 * correct / total
print(f"Validation Accuracy: {val_accuracy:.2f}%")

# ----------------------------
# Save
# ----------------------------
torch.save(model.state_dict(), "resnet18_tomato_disease_5class.pth")
print("Saved: resnet18_tomato_disease_5class.pth")

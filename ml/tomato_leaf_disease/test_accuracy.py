import torch
import torch.nn as nn
import torchvision.transforms as transforms
from torchvision import datasets, models
from torch.utils.data import DataLoader
import matplotlib.pyplot as plt
import numpy as np
from sklearn.metrics import (
    confusion_matrix,
    classification_report,
    roc_curve,
    roc_auc_score
)
import seaborn as sns
from sklearn.preprocessing import label_binarize

# Set device
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

# Transforms
test_transforms = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406],
                         std=[0.229, 0.224, 0.225])
])

# Dataset & DataLoader
test_dataset_path = r"Tomato Leaf Disease.v1i.folder (1)/test"
test_dataset = datasets.ImageFolder(root=test_dataset_path, transform=test_transforms)
test_loader = DataLoader(test_dataset, batch_size=32, shuffle=False, pin_memory=True if device.type == "cuda" else False)

class_names = test_dataset.classes
num_classes = len(class_names)

# Load model
model = models.resnet18(pretrained=False)
model.fc = nn.Linear(model.fc.in_features, num_classes)

try:
    model.load_state_dict(torch.load("resnet18_tomato_disease_5class.pth", map_location=device))
    print("✅ Model loaded successfully!")
except Exception as e:
    print(f"❌ Error loading model: {e}")
    exit()

model.to(device)
model.eval()

# Evaluation function
def evaluate_model(model, loader):
    y_true = []
    y_pred = []
    y_score = []

    with torch.no_grad():
        for images, labels in loader:
            images, labels = images.to(device), labels.to(device)
            outputs = model(images)
            _, preds = torch.max(outputs, 1)

            y_true.extend(labels.cpu().numpy())
            y_pred.extend(preds.cpu().numpy())
            y_score.extend(outputs.cpu().numpy())

    return np.array(y_true), np.array(y_pred), np.array(y_score)

# Get predictions
y_true, y_pred, y_score = evaluate_model(model, test_loader)

# Accuracy
test_accuracy = 100 * (y_true == y_pred).sum() / len(y_true)
print(f"\n🔥 Test Accuracy: {test_accuracy:.2f}%")

# Confusion Matrix
conf_matrix = confusion_matrix(y_true, y_pred)
plt.figure(figsize=(8, 6))
sns.heatmap(conf_matrix, annot=True, fmt="d", xticklabels=class_names, yticklabels=class_names, cmap="Blues")
plt.title("Confusion Matrix")
plt.xlabel("Predicted Label")
plt.ylabel("True Label")
plt.show()

# Classification Report
print("\n📋 Classification Report:")
print(classification_report(y_true, y_pred, target_names=class_names))

# ROC Curve & AUC
y_true_bin = label_binarize(y_true, classes=range(num_classes))  # One-hot encode
fpr = dict()
tpr = dict()
roc_auc = dict()

for i in range(num_classes):
    fpr[i], tpr[i], _ = roc_curve(y_true_bin[:, i], y_score[:, i])
    roc_auc[i] = roc_auc_score(y_true_bin[:, i], y_score[:, i])

plt.figure(figsize=(10, 6))
for i in range(num_classes):
    plt.plot(fpr[i], tpr[i], label=f"{class_names[i]} (AUC = {roc_auc[i]:.2f})")
plt.plot([0, 1], [0, 1], 'k--', label='Random')
plt.xlabel("False Positive Rate")
plt.ylabel("True Positive Rate")
plt.title("ROC Curve")
plt.legend(loc="lower right")
plt.grid()
plt.show()

# Class-wise Accuracy
print("\n📊 Class-wise Accuracy:")
for i, class_name in enumerate(class_names):
    class_total = np.sum(y_true == i)
    class_correct = np.sum((y_true == i) & (y_pred == i))
    accuracy = 100 * class_correct / class_total if class_total > 0 else 0
    print(f"{class_name}: {accuracy:.2f}%")

# Visual Predictions
def imshow(img, title):
    img = img.permute(1, 2, 0).numpy()
    img = img * np.array([0.229, 0.224, 0.225]) + np.array([0.485, 0.456, 0.406])  # unnormalize
    img = np.clip(img, 0, 1)
    plt.imshow(img)
    plt.title(title)
    plt.axis("off")

def show_predictions(model, loader, num_images=6):
    model.eval()
    images_shown = 0
    plt.figure(figsize=(12, 8))
    with torch.no_grad():
        for images, labels in loader:
            outputs = model(images.to(device))
            _, preds = torch.max(outputs, 1)
            for i in range(images.size(0)):
                if images_shown >= num_images:
                    return
                plt.subplot(2, 3, images_shown + 1)
                title = f"Pred: {class_names[preds[i]]}\nTrue: {class_names[labels[i]]}"
                imshow(images[i].cpu(), title)
                images_shown += 1
    plt.tight_layout()
    plt.show()

show_predictions(model, test_loader)

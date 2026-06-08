import os
import torch
import torch.nn as nn
import torchvision.transforms as transforms
from torchvision import datasets, models
from torch.utils.data import DataLoader
import matplotlib.pyplot as plt
import numpy as np
from sklearn.metrics import confusion_matrix, classification_report, roc_curve, roc_auc_score
import seaborn as sns
from sklearn.preprocessing import label_binarize

# Create output dir
output_dir = "academic_dataset_eval"
if not os.path.exists(output_dir):
    os.makedirs(output_dir)

# Set device
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

def evaluate_tomato_disease_model():
    print("Evaluating ROBUST Tomato Disease Model on Academic Validation Split...")
    test_transforms = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
    ])

    test_dataset_path = r"../Tomato Leaf Disease.v1i.folder (1)/test"
    test_dataset = datasets.ImageFolder(root=test_dataset_path, transform=test_transforms)
    test_loader = DataLoader(test_dataset, batch_size=32, shuffle=False, pin_memory=True if device.type == "cuda" else False)

    class_names = test_dataset.classes
    num_classes = len(class_names)

    model = models.resnet18(weights=None)
    model.fc = nn.Linear(model.fc.in_features, num_classes)
    
    try:
        model.load_state_dict(torch.load("resnet18_tomato_disease_robust.pth", map_location=device))
        print("Model loaded successfully!")
    except Exception as e:
        print(f"Error loading model: {e}")
        return

    model.to(device)
    model.eval()

    y_true = []
    y_pred = []
    y_score = []

    with torch.no_grad():
        for images, labels in test_loader:
            images, labels = images.to(device), labels.to(device)
            outputs = model(images)
            _, preds = torch.max(outputs, 1)

            y_true.extend(labels.cpu().numpy())
            y_pred.extend(preds.cpu().numpy())
            y_score.extend(outputs.cpu().numpy())

    y_true = np.array(y_true)
    y_pred = np.array(y_pred)
    y_score = np.array(y_score)

    # Accuracy
    test_accuracy = 100 * (y_true == y_pred).sum() / len(y_true)
    print(f"Test Accuracy: {test_accuracy:.2f}%")

    with open(os.path.join(output_dir, "classification_report.txt"), "w") as f:
        f.write(f"Test Accuracy: {test_accuracy:.2f}%\n\n")
        f.write("Classification Report:\n")
        f.write(classification_report(y_true, y_pred, target_names=class_names))

    # Confusion Matrix
    conf_matrix = confusion_matrix(y_true, y_pred)
    plt.figure(figsize=(8, 6))
    sns.heatmap(conf_matrix, annot=True, fmt="d", xticklabels=class_names, yticklabels=class_names, cmap="Blues")
    plt.title("Confusion Matrix")
    plt.xlabel("Predicted Label")
    plt.ylabel("True Label")
    plt.savefig(os.path.join(output_dir, "confusion_matrix.png"))
    plt.close()

    print("Evaluation results saved to model_tests folder.")

if __name__ == '__main__':
    evaluate_tomato_disease_model()

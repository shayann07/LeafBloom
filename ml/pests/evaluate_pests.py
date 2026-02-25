import torch
import torch.nn as nn
from torchvision import datasets, transforms, models
import os
import numpy as np
from sklearn.metrics import classification_report, confusion_matrix
import matplotlib.pyplot as plt
import seaborn as sns

# Config
DATA_DIR = r"d:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\pests\dataset"
MODEL_PATH = "pest_id_model.pth"
BATCH_SIZE = 32
# Alphabetical order matching training (12 Classes)
CLASSES = ['ants', 'bees', 'beetle', 'catterpillar', 'earthworms', 'earwig', 'grasshopper', 'moth', 'slug', 'snail', 'wasp', 'weevil']
DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")

def load_model():
    print(f"Loading {MODEL_PATH}...")
    model = models.resnet18(weights=None)
    num_ftrs = model.fc.in_features
    # Match the Sequential structure from training
    model.fc = nn.Sequential(
        nn.Dropout(0.3),
        nn.Linear(num_ftrs, len(CLASSES))
    )
    
    if os.path.exists(MODEL_PATH):
        model.load_state_dict(torch.load(MODEL_PATH, map_location=DEVICE))
    else:
        print("❌ Model file not found!")
        return None
        
    model = model.to(DEVICE)
    model.eval()
    return model

def evaluate():
    model = load_model()
    if model is None: return

    # Validation Transform (Resize 256 -> CenterCrop 224 for correct aspect ratio)
    test_transform = transforms.Compose([
        transforms.Resize(256),
        transforms.CenterCrop(224),
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
    ])

    test_dir = os.path.join(DATA_DIR, "test")
    if not os.path.exists(test_dir):
        print(f"❌ Test directory not found: {test_dir}")
        return

    test_dataset = datasets.ImageFolder(test_dir, transform=test_transform)
    test_loader = torch.utils.data.DataLoader(test_dataset, batch_size=BATCH_SIZE, shuffle=False)

    print(f"Evaluating on {len(test_dataset)} test images...")
    
    all_preds = []
    all_labels = []

    with torch.no_grad():
        for inputs, labels in test_loader:
            inputs = inputs.to(DEVICE)
            labels = labels.to(DEVICE)
            
            outputs = model(inputs)
            _, preds = torch.max(outputs, 1)
            
            all_preds.extend(preds.cpu().numpy())
            all_labels.extend(labels.cpu().numpy())

    # Metrics
    print("\n" + "="*50)
    print("CLASSIFICATION REPORT")
    print("="*50)
    print(classification_report(all_labels, all_preds, target_names=CLASSES, digits=4))

    print("\n" + "="*50)
    print("CONFUSION MATRIX")
    print("="*50)
    cm = confusion_matrix(all_labels, all_preds)
    print(cm)
    
    # Calculate Overall Accuracy
    accuracy = np.sum(np.diag(cm)) / np.sum(cm)
    print("\n" + "="*50)
    print(f"OVERALL ACCURACY: {accuracy * 100:.2f}%")
    print("="*50)

    # Plot Confusion Matrix
    print("Generating Confusion Matrix Plot...")
    plt.figure(figsize=(14, 12))
    sns.heatmap(cm, annot=True, fmt='d', cmap='Blues', xticklabels=CLASSES, yticklabels=CLASSES)
    plt.xlabel('Predicted Label')
    plt.ylabel('True Label')
    plt.title(f'Confusion Matrix - Accuracy: {accuracy*100:.2f}%')
    plt.tight_layout()
    plt.savefig('confusion_matrix.png')
    print("✅ Confusion matrix saved as 'confusion_matrix.png'")

if __name__ == "__main__":
    evaluate()

import torch
import torch.nn as nn
import torch.optim as optim
from torchvision import datasets, transforms, models
import os
import time

# Config
DATA_DIR = r"d:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\pests\dataset"
BATCH_SIZE = 32
LEARNING_RATE = 0.0001
SAVE_PATH = "pest_id_model.pth"
PATIENCE = 5

def set_seed(seed=42):
    torch.manual_seed(seed)
    torch.cuda.manual_seed(seed)
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False
    import random
    import numpy as np
    random.seed(seed)
    np.random.seed(seed)

def get_class_weights(dataset, device):
    import numpy as np
    targets = dataset.targets
    class_counts = np.bincount(targets)
    total_samples = len(dataset)
    num_classes = len(dataset.classes)
    weights = total_samples / (num_classes * class_counts)
    return torch.tensor(weights, dtype=torch.float).to(device)

def train_one_epoch(model, loader, criterion, optimizer, device):
    model.train()
    running_loss = 0.0
    correct = 0
    total = 0
    for inputs, labels in loader:
        inputs, labels = inputs.to(device), labels.to(device)
        optimizer.zero_grad()
        outputs = model(inputs)
        loss = criterion(outputs, labels)
        loss.backward()
        optimizer.step()
        running_loss += loss.item()
        _, predicted = torch.max(outputs.data, 1)
        total += labels.size(0)
        correct += (predicted == labels).sum().item()
    return running_loss / len(loader), 100 * correct / total

def validate(model, loader, device):
    model.eval()
    val_correct = 0
    val_total = 0
    with torch.no_grad():
        for inputs, labels in loader:
            inputs, labels = inputs.to(device), labels.to(device)
            outputs = model(inputs)
            _, predicted = torch.max(outputs.data, 1)
            val_total += labels.size(0)
            val_correct += (predicted == labels).sum().item()
    return 100 * val_correct / val_total

def main():
    set_seed()
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Using device: {device}")

    # Transforms
    # Transforms
    # Transforms
    train_transform = transforms.Compose([
        transforms.RandomResizedCrop(224, scale=(0.75, 1.0)), 
        transforms.RandomHorizontalFlip(),
        transforms.RandomRotation(30),
        transforms.RandomAffine(degrees=0, translate=(0.05, 0.05), scale=None, shear=5),
        transforms.ColorJitter(brightness=0.15, contrast=0.15, saturation=0.15, hue=0.03), # Safer Hue
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
    ])

    val_transform = transforms.Compose([
        transforms.Resize(256),      # Resize shorter side to 256
        transforms.CenterCrop(224),  # Center crop to 224 (Preserves Aspect Ratio)
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
    ])

    # Load Data
    train_dir = os.path.join(DATA_DIR, "train")
    val_dir = os.path.join(DATA_DIR, "test")

    train_dataset = datasets.ImageFolder(train_dir, transform=train_transform)
    val_dataset = datasets.ImageFolder(val_dir, transform=val_transform)

    train_loader = torch.utils.data.DataLoader(train_dataset, batch_size=BATCH_SIZE, shuffle=True)
    val_loader = torch.utils.data.DataLoader(val_dataset, batch_size=BATCH_SIZE, shuffle=False)

    print(f"Classes ({len(train_dataset.classes)}): {train_dataset.classes}")
    
    # Class Weights
    class_weights = get_class_weights(train_dataset, device)
    criterion = nn.CrossEntropyLoss(weight=class_weights, label_smoothing=0.05) 

    # Model Setup
    print("Initializing ResNet18 (Weights=DEFAULT)...")
    model = models.resnet18(weights=models.ResNet18_Weights.DEFAULT)
    
    # Freeze Backbone (Feature Extractor)
    for param in model.parameters():
        param.requires_grad = False
        
    num_ftrs = model.fc.in_features
    # New Head with Dropout (0.3)
    model.fc = nn.Sequential(
        nn.Dropout(0.3), 
        nn.Linear(num_ftrs, len(train_dataset.classes))
    )
    model = model.to(device)

    # Phase 1: Train Head Only
    print("\n--- PHASE 1: Training Head (5 Epochs) ---")
    optimizer = optim.AdamW(model.fc.parameters(), lr=0.001, weight_decay=2e-4) # AdamW
    
    for epoch in range(5):
        loss, acc = train_one_epoch(model, train_loader, criterion, optimizer, device)
        val_acc = validate(model, val_loader, device)
        print(f"Epoch {epoch+1}/5 | Loss: {loss:.4f} | Train Acc: {acc:.2f}% | Val Acc: {val_acc:.2f}%")

    # Phase 2: Fine-Tuning (Unfreeze All)
    print("\n--- PHASE 2: Fine-Tuning Entire Model (25 Epochs) ---") 
    for param in model.parameters():
        param.requires_grad = True
        
    optimizer = optim.AdamW(model.parameters(), lr=0.0001, weight_decay=2e-4) # AdamW
    scheduler = optim.lr_scheduler.ReduceLROnPlateau(optimizer, mode='max', factor=0.1, patience=3, verbose=True) 
    
    best_acc = 0.0
    epochs_no_improve = 0
    
    for epoch in range(25): 
        loss, acc = train_one_epoch(model, train_loader, criterion, optimizer, device)
        
        val_acc = validate(model, val_loader, device)
        scheduler.step(val_acc) 
        
        print(f"Epoch {epoch+1}/25 | Loss: {loss:.4f} | Train Acc: {acc:.2f}% | Val Acc: {val_acc:.2f}%")

        if val_acc > best_acc:
            best_acc = val_acc
            epochs_no_improve = 0
            torch.save(model.state_dict(), SAVE_PATH)
            print(f"   ✅ New Best Model Saved! ({best_acc:.2f}%)")
        else:
            epochs_no_improve += 1
            print(f"   ⚠️ No improvement for {epochs_no_improve} epochs.")
            
        if epochs_no_improve >= PATIENCE:
            print(f"\n⏹️ Early Stopping triggered! Best Accuracy: {best_acc:.2f}%")
            break

    print(f"\nTraining Complete. Best Validation Accuracy: {best_acc:.2f}%")

if __name__ == "__main__":
    main()

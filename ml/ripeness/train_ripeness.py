import torch
import torch.nn as nn
import torch.optim as optim
from torchvision import datasets, transforms, models
import os

# Config
DATA_DIR = r"d:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\ripeness\dataset"
BATCH_SIZE = 32
EPOCHS = 20 # Limit set to 20, but Early Stopping will likely stop it sooner
LEARNING_RATE = 0.0001
SAVE_PATH = "ripeness_3class.pth"
PATIENCE = 6 # Stop if no improvement for 6 epochs

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
    
    # Formula: Total / (NumClasses * ClassCount)
    weights = total_samples / (num_classes * class_counts)
    print(f"Dataset Counts: {dict(zip(dataset.classes, class_counts))}")
    print(f"Calculated Weights: {weights}")
    
    return torch.tensor(weights, dtype=torch.float).to(device)

def main():
    set_seed()
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Using device: {device}")

    # Transforms (Aggressive Augmentation + Scale Invariance)
    train_transform = transforms.Compose([
        transforms.RandomResizedCrop(224, scale=(0.8, 1.0)), # Capture object at different scales to fix "zoom" bias
        transforms.RandomHorizontalFlip(),
        transforms.RandomVerticalFlip(), 
        transforms.RandomRotation(30),
        transforms.ColorJitter(brightness=0.2, contrast=0.2, saturation=0.2, hue=0.05),
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
    ])

    val_transform = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
    ])

    # Load Data
    train_dir = os.path.join(DATA_DIR, "Train")
    val_dir = os.path.join(DATA_DIR, "Test")

    if not os.path.exists(train_dir):
        print(f"❌ Error: Train directory not found at {train_dir}")
        return

    train_dataset = datasets.ImageFolder(train_dir, transform=train_transform)
    val_dataset = datasets.ImageFolder(val_dir, transform=val_transform)

    train_loader = torch.utils.data.DataLoader(train_dataset, batch_size=BATCH_SIZE, shuffle=True)
    val_loader = torch.utils.data.DataLoader(val_dataset, batch_size=BATCH_SIZE, shuffle=False)

    print(f"Classes: {train_dataset.classes}")
    
    # Dynamic Class Weights
    class_weights = get_class_weights(train_dataset, device)

    # Model (ResNet18)
    print("Initializing ResNet18...")
    model = models.resnet18(pretrained=True)
    num_ftrs = model.fc.in_features
    model.fc = nn.Linear(num_ftrs, len(train_dataset.classes))
    model = model.to(device)

    criterion = nn.CrossEntropyLoss(weight=class_weights)
    optimizer = optim.Adam(model.parameters(), lr=LEARNING_RATE)
    scheduler = optim.lr_scheduler.StepLR(optimizer, step_size=7, gamma=0.1)

    best_acc = 0.0
    epochs_no_improve = 0

    # Training Loop
    print(f"\nStarting training (Max Epochs: {EPOCHS}, Patience: {PATIENCE})...")
    for epoch in range(EPOCHS):
        model.train()
        running_loss = 0.0
        correct = 0
        total = 0

        for inputs, labels in train_loader:
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
        
        scheduler.step()

        epoch_acc = 100 * correct / total
        print(f"Epoch {epoch+1}/{EPOCHS} | Loss: {running_loss/len(train_loader):.4f} | Train Acc: {epoch_acc:.2f}%")

        # Validation
        model.eval()
        val_correct = 0
        val_total = 0
        with torch.no_grad():
            for inputs, labels in val_loader:
                inputs, labels = inputs.to(device), labels.to(device)
                outputs = model(inputs)
                _, predicted = torch.max(outputs.data, 1)
                val_total += labels.size(0)
                val_correct += (predicted == labels).sum().item()

        val_acc = 100 * val_correct / val_total
        print(f"   Validation Acc: {val_acc:.2f}%")

        # Save Best Model & Early Stopping
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
    print(f"Model saved to {SAVE_PATH}")
    print(f"Class Mapping: {train_dataset.class_to_idx}")

if __name__ == "__main__":
    main()

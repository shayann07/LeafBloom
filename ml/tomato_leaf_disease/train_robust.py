import os
import torch
import torch.nn as nn
import torch.optim as optim
from torchvision import datasets, transforms, models
from torch.utils.data import DataLoader
from torch.optim.lr_scheduler import ReduceLROnPlateau
import datetime

def train_robust_model():
    print(f"[{datetime.datetime.now().strftime('%H:%M:%S')}] Initializing Robust Training Pipeline for Tomato Disease...")
    
    # Enable hardware acceleration
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Using device: {device}")

    # Aggressive Data Augmentation for Real-World Robustness
    train_transform = transforms.Compose([
        transforms.RandomResizedCrop(224, scale=(0.7, 1.0)), # Prevent reliance on center/scale
        transforms.RandomHorizontalFlip(p=0.5),
        transforms.RandomVerticalFlip(p=0.5),
        transforms.RandomRotation(degrees=30), # Real-world photos are rarely perfectly straight
        transforms.ColorJitter(brightness=0.3, contrast=0.3, saturation=0.3, hue=0.1), # Simulate different lighting/cameras
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
    ])

    val_transform = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
    ])

    # Dataset Paths from parent directory
    train_dir = r"../Tomato Leaf Disease.v1i.folder (1)/train"
    val_dir = r"../Tomato Leaf Disease.v1i.folder (1)/valid"

    # Verify datasets exist
    if not os.path.exists(train_dir) or not os.path.exists(val_dir):
        print(f"Dataset missing! Check paths: {train_dir}")
        return

    # Load datasets
    train_dataset = datasets.ImageFolder(root=train_dir, transform=train_transform)
    val_dataset = datasets.ImageFolder(root=val_dir, transform=val_transform)

    class_names = train_dataset.classes
    num_classes = len(class_names)
    print(f"Training on {len(train_dataset)} images | Validating on {len(val_dataset)} images")
    print(f"Classes: {class_names}")

    # Dataloaders - RTX 3050 6GB handles batch size 32 comfortably for ResNet18
    # Windows prefers num_workers=0 to avoid multiprocessing pickling errors inside loops, but 2-4 is fast if guarded.
    train_loader = DataLoader(train_dataset, batch_size=32, shuffle=True, pin_memory=True if device.type == "cuda" else False, num_workers=4)
    val_loader = DataLoader(val_dataset, batch_size=32, shuffle=False, pin_memory=True if device.type == "cuda" else False, num_workers=4)

    # Initialize Pre-trained ResNet18
    model = models.resnet18(weights=models.ResNet18_Weights.DEFAULT)
    
    # Replace final classification head
    model.fc = nn.Linear(model.fc.in_features, num_classes)
    model = model.to(device)

    # Optimization Setup
    # Label smoothing acts as powerful regularization, forcing model to not be overconfident
    criterion = nn.CrossEntropyLoss(label_smoothing=0.1) 
    
    # AdamW incorporates weight decay natively, superior for ResNet architectures
    optimizer = optim.AdamW(model.parameters(), lr=0.001, weight_decay=1e-4)
    
    # Scheduler: Reduces Learning Rate if validation loss stagnates for 2 epochs
    scheduler = ReduceLROnPlateau(optimizer, mode='min', factor=0.5, patience=2)

    # Training Loop configuration
    epochs = 20
    best_val_loss = float('inf')
    best_model_path = "resnet18_tomato_disease_robust.pth"

    print("\nStarting Training...\n" + "="*50)
    for epoch in range(epochs):
        # --- TRAINING PHASE ---
        model.train()
        running_loss = 0.0
        correct_train = 0
        total_train = 0

        for images, labels in train_loader:
            images, labels = images.to(device), labels.to(device)

            optimizer.zero_grad()
            outputs = model(images)
            loss = criterion(outputs, labels)
            loss.backward()
            optimizer.step()

            running_loss += loss.item() * images.size(0)
            
            _, predicted = outputs.max(1)
            total_train += labels.size(0)
            correct_train += predicted.eq(labels).sum().item()

        epoch_train_loss = running_loss / len(train_dataset)
        epoch_train_acc = 100. * correct_train / total_train

        # --- VALIDATION PHASE ---
        model.eval()
        val_loss = 0.0
        correct_val = 0
        total_val = 0

        with torch.no_grad():
            for images, labels in val_loader:
                images, labels = images.to(device), labels.to(device)
                
                outputs = model(images)
                loss = criterion(outputs, labels)
                
                val_loss += loss.item() * images.size(0)
                _, predicted = outputs.max(1)
                total_val += labels.size(0)
                correct_val += predicted.eq(labels).sum().item()

        epoch_val_loss = val_loss / len(val_dataset)
        epoch_val_acc = 100. * correct_val / total_val

        # Print Epoch Analytics
        print(f"Epoch [{epoch+1}/{epochs}]")
        print(f"  Train Loss: {epoch_train_loss:.4f} | Train Acc: {epoch_train_acc:.2f}%")
        print(f"  Valid Loss: {epoch_val_loss:.4f} | Valid Acc: {epoch_val_acc:.2f}%")

        # Step Model Scheduler
        scheduler.step(epoch_val_loss)

        # Save Best Model Logic
        if epoch_val_loss < best_val_loss:
            best_val_loss = epoch_val_loss
            print(f"  --> Validation Loss improved! Saving optimal weights to {best_model_path}")
            torch.save(model.state_dict(), best_model_path)
            
        print("-" * 50)

    print(f"\nTraining Complete. Best Validation Loss: {best_val_loss:.4f}")
    print(f"Optimal Model weights saved as '{best_model_path}'")

if __name__ == '__main__':
    # Required for Windows multiprocessing (Dataloader num_workers)
    import multiprocessing
    multiprocessing.freeze_support()
    train_robust_model()

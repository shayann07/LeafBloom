"""Evaluate the MOBILE .ptl models from assets against full test datasets."""
import torch
from torchvision import datasets, transforms
import numpy as np
from sklearn.metrics import classification_report, confusion_matrix

BATCH_SIZE = 32

def evaluate_mobile_model(model_path, test_dir, classes, transform):
    print(f"\nLoading MOBILE model: {model_path}")
    model = torch.jit.load(model_path)
    model.eval()

    test_dataset = datasets.ImageFolder(test_dir, transform=transform)
    test_loader = torch.utils.data.DataLoader(test_dataset, batch_size=BATCH_SIZE, shuffle=False)

    print(f"Evaluating on {len(test_dataset)} test images...")
    print(f"Dataset classes: {test_dataset.classes}")

    all_preds = []
    all_labels = []

    with torch.no_grad():
        for inputs, labels in test_loader:
            # .ptl model already has Softmax baked in
            probs = model(inputs)
            _, preds = torch.max(probs, 1)
            all_preds.extend(preds.cpu().numpy())
            all_labels.extend(labels.cpu().numpy())

    print("\n" + "="*50)
    print("CLASSIFICATION REPORT")
    print("="*50)
    print(classification_report(all_labels, all_preds, target_names=classes, digits=4))

    cm = confusion_matrix(all_labels, all_preds)
    print("CONFUSION MATRIX")
    print(cm)

    accuracy = np.sum(np.diag(cm)) / np.sum(cm)
    print(f"\nOVERALL ACCURACY: {accuracy * 100:.2f}%")
    return accuracy

if __name__ == "__main__":
    # --- PEST MODEL (from assets) ---
    print("=" * 60)
    print("PEST IDENTIFICATION MODEL (.ptl from assets)")
    print("=" * 60)

    pest_transform = transforms.Compose([
        transforms.Resize(256),
        transforms.CenterCrop(224),
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
    ])

    pest_classes = ['ants', 'bees', 'beetle', 'catterpillar', 'earthworms',
                    'earwig', 'grasshopper', 'moth', 'slug', 'snail', 'wasp', 'weevil']

    pest_acc = evaluate_mobile_model(
        model_path=r"d:\Work\AndroidStudioProjects\FYP\LeafBloom\app\src\main\assets\pest_id_model.ptl",
        test_dir=r"d:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\pests\dataset\test",
        classes=pest_classes,
        transform=pest_transform
    )

    # --- RIPENESS MODEL (from assets) ---
    print("\n" + "=" * 60)
    print("RIPENESS CHECK MODEL (.ptl from assets)")
    print("=" * 60)

    ripeness_transform = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
    ])

    ripeness_classes = ['Ripe', 'Unknown', 'Unripe']

    ripeness_acc = evaluate_mobile_model(
        model_path=r"d:\Work\AndroidStudioProjects\FYP\LeafBloom\app\src\main\assets\ripeness_model.ptl",
        test_dir=r"d:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\ripeness\dataset\Test",
        classes=ripeness_classes,
        transform=ripeness_transform
    )

    print("\n" + "=" * 60)
    print("SUMMARY")
    print("=" * 60)
    print(f"Pest Model (.ptl):     {pest_acc * 100:.2f}%")
    print(f"Ripeness Model (.ptl): {ripeness_acc * 100:.2f}%")

import os
import random
import matplotlib.pyplot as plt
from PIL import Image

DATA_DIR = r"d:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\ripeness\dataset\Train"
CLASSES = ['Ripe', 'Unknown', 'Unripe']

def visualize_dataset_samples():
    # Only visualizing Ripe and Unknown since Unripe seems fine
    target_classes = ['Ripe', 'Unknown'] 
    
    fig, axes = plt.subplots(len(target_classes), 5, figsize=(15, 6))
    
    for i, cls in enumerate(target_classes):
        class_dir = os.path.join(DATA_DIR, cls)
        if not os.path.exists(class_dir):
            continue
            
        files = [f for f in os.listdir(class_dir) if f.endswith(('.jpg', '.png', '.jpeg'))]
        if not files:
            print(f"No files in {cls}")
            continue
            
        random.shuffle(files)
        selected = files[:5]
        
        for j, fname in enumerate(selected):
            img_path = os.path.join(class_dir, fname)
            try:
                img = Image.open(img_path)
                ax = axes[i, j]
                ax.imshow(img)
                ax.axis('off')
                if j == 0:
                    ax.set_title(cls, fontsize=14, loc='left')
            except Exception as e:
                print(f"Error loading {fname}: {e}")

    plt.tight_layout()
    plt.savefig("debug_dataset_content.png")
    print("Saved debug_dataset_content.png")

if __name__ == "__main__":
    visualize_dataset_samples()

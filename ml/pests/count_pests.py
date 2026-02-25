import os

DATA_DIR = r"d:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\pests\dataset"
SPLITS = ['train', 'test']

def count_images():
    if not os.path.exists(DATA_DIR):
        print(f"❌ Error: Dataset directory not found at {DATA_DIR}")
        return

    print(f"{'Class':<15} | {'Train':<10} | {'Test':<10} | {'Total':<10}")
    print("-" * 55)

    # Get all classes from train folder
    train_path = os.path.join(DATA_DIR, 'train')
    if not os.path.exists(train_path):
         print("❌ Error: 'train' folder not found.")
         return
         
    classes = sorted([d for d in os.listdir(train_path) if os.path.isdir(os.path.join(train_path, d))])
    
    total_train = 0
    total_test = 0

    for cls in classes:
        train_count = 0
        test_count = 0
        
        # Count Train
        cls_train_path = os.path.join(DATA_DIR, 'train', cls)
        if os.path.exists(cls_train_path):
            train_count = len([f for f in os.listdir(cls_train_path) if f.lower().endswith(('.jpg', '.jpeg', '.png'))])
        
        # Count Test
        cls_test_path = os.path.join(DATA_DIR, 'test', cls)
        if os.path.exists(cls_test_path):
            test_count = len([f for f in os.listdir(cls_test_path) if f.lower().endswith(('.jpg', '.jpeg', '.png'))])
            
        total_train += train_count
        total_test += test_count
        
        print(f"{cls:<15} | {train_count:<10} | {test_count:<10} | {train_count + test_count:<10}")

    print("-" * 55)
    print(f"{'TOTAL':<15} | {total_train:<10} | {total_test:<10} | {total_train + total_test:<10}")

if __name__ == "__main__":
    count_images()


import os

DATASET_DIR = r"D:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\nutrient_deficiency\dataset_tomato"
IMAGE_EXTENSIONS = ('.jpg', '.jpeg', '.png', '.bmp', '.webp')

def analyze_structure(root_dir):
    print(f"Analyzing: {root_dir}\n")
    print(f"{'Folder Hierarchy':<60} | {'Images':<10}")
    print("-" * 75)

    total_images = 0
    
    # Walk the directory
    for root, dirs, files in os.walk(root_dir):
        # Count images in current dir
        image_files = [f for f in files if f.lower().endswith(IMAGE_EXTENSIONS)]
        count = len(image_files)
        total_images += count
        
        # Calculate indentation for tree structure
        rel_path = os.path.relpath(root, root_dir)
        if rel_path == ".":
            display_name = os.path.basename(root_dir)
            level = 0
        else:
            display_name = os.path.basename(root)
            level = rel_path.count(os.sep) + 1
            
        indent = "  " * level
        folder_str = f"{indent}|-- {display_name}"
        
        # Only print folders that have content or subfolders
        # We perform a check: does it have images? if yes, print.
        # If no images, but has subdirs, we usually let the subdirs print themselves.
        # But to show the structure, we print everything.
        
        print(f"{folder_str:<60} | {count:<10}")

    print("-" * 75)
    print(f"Total Images Found: {total_images}")

if __name__ == "__main__":
    DIRS_TO_SCAN = [
        r"D:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\nutrient_deficiency\dataset",
        r"D:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\nutrient_deficiency\datasets_downloaded",
        r"D:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\nutrient_deficiency\dataset_tomato",
        r"D:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\nutrient_deficiency\Tomato Dataset"
    ]
    
    for d in DIRS_TO_SCAN:
        if os.path.exists(d):
            analyze_structure(d)
        else:
            print(f"Directory not found: {d}\n")
        print("\n" + "="*80 + "\n")

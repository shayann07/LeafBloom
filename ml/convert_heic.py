
import os
import sys
from PIL import Image
try:
    from pillow_heif import register_heif_opener
    register_heif_opener()
except ImportError:
    print("pillow-heif not found. Attempting basic open...")

def convert_heic_to_jpg(folder):
    print(f"Scanning {folder}...")
    count = 0
    for filename in os.listdir(folder):
        ext = filename.lower()
        if ext.endswith((".heic", ".png", ".jpeg", ".jpg")):
            # Convert everything to .jpg for consistency, except if it is already .jpg
            if ext.endswith(".jpg"):
                continue

            heic_path = os.path.join(folder, filename)
            jpg_path = os.path.splitext(heic_path)[0] + ".jpg"
            
            try:
                img = Image.open(heic_path)
                img = img.convert("RGB")
                img.save(jpg_path, "JPEG")
                print(f"Converted: {filename} -> {os.path.basename(jpg_path)}")
                
                # Close and remove original
                img.close()
                os.remove(heic_path)
                count += 1
            except Exception as e:
                print(f"Failed to convert {filename}: {e}")
    print(f"Converted {count} images in {folder}")

if __name__ == "__main__":
    train_unknown = "Tomato Leaf Disease.v1i.folder (1)/train/00_UNKNOWN"
    valid_unknown = "Tomato Leaf Disease.v1i.folder (1)/valid/00_UNKNOWN"
    
    test_unknown = "Tomato Leaf Disease.v1i.folder (1)/test/00_UNKNOWN"

    if os.path.exists(train_unknown):
        convert_heic_to_jpg(train_unknown)
    
    if os.path.exists(valid_unknown):
        convert_heic_to_jpg(valid_unknown)
        
    if os.path.exists(test_unknown):
        convert_heic_to_jpg(test_unknown)

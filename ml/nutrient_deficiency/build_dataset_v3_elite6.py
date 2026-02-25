import os
import re
import math
import json
import shutil
import random
import hashlib
import csv
from dataclasses import dataclass
from collections import Counter
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from PIL import Image, ImageOps
from tqdm import tqdm

# ============================================================
# CONFIG
# ============================================================
SEED = 42
IMG_SIZE = 384  # New target resolution for Elite 6
CACHE_SIZES = [384]  # Optimized build (removed 512, 640)
JPEG_QUALITY = 90

ROOT = Path(r"D:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\nutrient_deficiency")

OUTPUT_DIR = ROOT / f"dataset_v3_elite6_{IMG_SIZE}px" 
# Primary cache dir linked to current IMG_SIZE
CACHE_DIR = OUTPUT_DIR / f"_cache_{IMG_SIZE}"

# Elite 6 (single-label)
CLASSES = ["Calcium", "Healthy", "Nitrogen", "Phosphorus", "Potassium", "Sulphur"]
CLASS_SET = set(CLASSES)

# Holdout sizing (robust for small/large classes)
VAL_PER_CLASS = 120
TEST_PER_CLASS = 120
MIN_VAL_PER_CLASS = 30
MIN_TEST_PER_CLASS = 30

# Speed/SSD safety
USE_HARDLINKS = True

# Skip blurry dataset + old build artifacts
SKIP_SUBSTRINGS = [
    str((ROOT / "datasets_downloaded" / "NitrogenDeficiencyImage")).lower(),
    "dataset_final",
    "dataset_elite",
    "dataset_v3",
    "dataset_final_v2",
]

IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}

# Sources
DOWNLOADED = ROOT / "datasets_downloaded"
SRC_BANANA_RAW = DOWNLOADED / "Nutrient Deficient RAW Images of Banana Leaves"
SRC_COLEAF = DOWNLOADED / "CoLeaf DATASET"
SRC_EARLYNSD = DOWNLOADED / "EarlyNSD"
SRC_MAIZE_P = DOWNLOADED / "Maize Phosphorus"
SRC_ORIGINAL_DATASET = ROOT / "dataset"

SOURCE_ROOTS = [
    SRC_ORIGINAL_DATASET,
    SRC_BANANA_RAW,
    SRC_COLEAF,
    SRC_EARLYNSD,
    SRC_MAIZE_P,
]


# ============================================================
# DATA TYPES
# ============================================================
@dataclass
class Occurrence:
    orig_path: str
    source: str
    folder: str

@dataclass
class HashGroup:
    # One unique image (by content hash) with possibly many occurrences
    h: str
    rep_cache_path: Path
    occs: List[Occurrence]  # ALL occurrences preserved


# ============================================================
# UTILS
# ============================================================
from concurrent.futures import ProcessPoolExecutor, as_completed

# ============================================================
# UTILS
# ============================================================
def set_seed(seed: int = 42) -> None:
    random.seed(seed)
    os.environ["PYTHONHASHSEED"] = str(seed)

def is_image(p: Path) -> bool:
    return p.is_file() and p.suffix.lower() in IMAGE_EXTS

def should_skip(p: Path) -> bool:
    s = str(p).lower()
    return any(sub in s for sub in SKIP_SUBSTRINGS)

def safe_mkdir(p: Path) -> None:
    p.mkdir(parents=True, exist_ok=True)

def sha1_text(s: str) -> str:
    return hashlib.sha1(s.encode("utf-8", errors="ignore")).hexdigest()

def sha1_file_bytes(p: Path, chunk_size: int = 1024 * 1024) -> str:
    h = hashlib.sha1()
    with p.open("rb") as f:
        while True:
            b = f.read(chunk_size)
            if not b:
                break
            h.update(b)
    return h.hexdigest()

def resize_short_side_and_center_crop(img: Image.Image, size: int) -> Image.Image:
    img = ImageOps.exif_transpose(img)
    img = img.convert("RGB")
    w, h = img.size
    if w <= 0 or h <= 0:
        raise ValueError("Invalid image dimension")

    scale = size / min(w, h)
    new_w = max(size, int(round(w * scale)))
    new_h = max(size, int(round(h * scale)))

    try:
        resample = Image.Resampling.LANCZOS
    except Exception:
        resample = Image.LANCZOS

    img = img.resize((new_w, new_h), resample=resample)

    left = int(round((new_w - size) / 2.0))
    top = int(round((new_h - size) / 2.0))
    return img.crop((left, top, left + size, top + size))

def hardlink_or_copy(src: Path, dst: Path) -> None:
    safe_mkdir(dst.parent)
    if dst.exists():
        return
    if USE_HARDLINKS:
        try:
            os.link(str(src), str(dst))
            return
        except Exception:
            pass
    shutil.copy2(str(src), str(dst))


# ============================================================
# MULTIPROCESSING HELPER
# ============================================================
def process_one_image_job(args) -> Optional[Dict]:
    """
    Worker function to process a single image.
    Args:
        src_img (Path): Path to source image
        cls (str): Class name
        root_out (Path): Root output dir (to find parent)
        cache_sizes (List[int]): List of sizes to gen
        img_size (int): Primary size
        jpeg_quality (int): Quality
    """
    src_img, cls, root_out, cache_sizes, img_size, jpeg_quality = args
    
    try:
        primary_out_path = None
        
        # We need to re-derive logic that was in the loop
        pass # just context
        
        # 1. Ensure ALL caches exist
        for size in cache_sizes:
            # Reconstruct c_dir path logic carefully
            # OUTPUT_DIR was passed as root_out
            c_dir = root_out.parent / f"_common_cache_{size}" / cls
            c_dir.mkdir(parents=True, exist_ok=True) # Ensure exists inside worker (race condition safe-ish if pre-created)
            
            st = src_img.stat()
            key = f"{src_img.resolve()}|{st.st_size}|{st.st_mtime_ns}|{size}|q{jpeg_quality}"
            out_name = hashlib.sha1(key.encode("utf-8", errors="ignore")).hexdigest() + ".jpg"
            out_path = c_dir / out_name
            
            if size == img_size:
                primary_out_path = out_path

            if not out_path.exists():
                try:
                    with Image.open(src_img) as im:
                         # Copied helper logic or import? Better to duplicate simple logic or rely on top-level func availability (multiprocessing picks up top-level funcs)
                         # resize_short_side_and_center_crop is top-level, so it pickles fine.
                        im_resized = resize_short_side_and_center_crop(im, size)
                        im_resized.save(out_path, format="JPEG", quality=jpeg_quality, optimize=True)
                except OSError:
                     return None # Corrupt
        
        if primary_out_path is None:
            return None

        # 2. Return metadata for main thread
        # We need the hash of the primary file
        h = hashlib.sha1()
        with primary_out_path.open("rb") as f:
            while True:
                b = f.read(1024*1024)
                if not b: break
                h.update(b)
        h_str = h.hexdigest()
        
        # Source/Folder keys need to be re-derived.
        # Since source_key/folder_key are top-level, we can call them.
        s = source_key(src_img)
        f = folder_key(src_img, s)
        
        return {
            "h": h_str,
            "primary_out_path": str(primary_out_path),
            "orig_path": str(src_img),
            "source": s,
            "folder": f,
            "cls": cls
        }

    except Exception:
        return None



# ============================================================
# SOURCE + FOLDER KEYS (for diversity metadata)
# ============================================================
def source_key(p: Path) -> str:
    pl = str(p).lower()
    if str(SRC_BANANA_RAW).lower() in pl:
        return "BANANA"
    if str(SRC_COLEAF).lower() in pl:
        return "COLEAF"
    if str(SRC_EARLYNSD).lower() in pl:
        return "EARLYNSD"
    if str(SRC_MAIZE_P).lower() in pl:
        return "MAIZE"
    if str(SRC_ORIGINAL_DATASET).lower() in pl:
        return "ORIG"
    return "OTHER"

def folder_key(p: Path, src: str) -> str:
    pl = str(p).lower()

    if src == "ORIG":
        rel = p.relative_to(SRC_ORIGINAL_DATASET)
        top = rel.parts[0].lower() if len(rel.parts) > 0 else "orig"
        top = re.sub(r"__part_\d+$", "", top)
        top = re.sub(r"__part\d+$", "", top)
        return top

    if src == "EARLYNSD":
        parent = p.parent.name.lower()  # ashgourd_fresh etc.
        plant = parent.split("_")[0] if "_" in parent else parent
        return plant

    if src == "MAIZE":
        return p.parent.name  # -P, -P50, _C

    return "all"


# ============================================================
# LABEL INFERENCE (Elite-6 only)
# ============================================================
def infer_label_from_path(p: Path) -> Optional[str]:
    pl = str(p).lower()

    # Banana RAW
    if str(SRC_BANANA_RAW).lower() in pl:
        for name in ["calcium", "healthy", "potassium", "sulphur"]:
            if f"\\{name}\\" in pl or f"/{name}/" in pl:
                return "Sulphur" if name == "sulphur" else name.capitalize()
        return None

    # CoLeaf
    if str(SRC_COLEAF).lower() in pl:
        mapping = {
            "calcium-ca": "Calcium",
            "healthy": "Healthy",
            "nitrogen-n": "Nitrogen",
            "phosphorus-p": "Phosphorus",
            "potasium-k": "Potassium",
        }
        for k, v in mapping.items():
            if f"\\{k}\\" in pl or f"/{k}/" in pl:
                return v
        return None

    # EarlyNSD
    if str(SRC_EARLYNSD).lower() in pl:
        if "fresh" in pl:
            return "Healthy"
        if "nitrogen" in pl:
            return "Nitrogen"
        if "potassium" in pl:
            return "Potassium"
        return None

    # Maize Phosphorus
    if str(SRC_MAIZE_P).lower() in pl:
        if "\\-p50\\" in pl or "/-p50/" in pl:
            return "Phosphorus"
        if "\\-p\\" in pl or "/-p/" in pl:
            return "Phosphorus"
        if "\\_c\\" in pl or "/_c/" in pl:
            return "Healthy"
        return None

    # Your mixed dataset (ONLY clean nutrient folders)
    if str(SRC_ORIGINAL_DATASET).lower() in pl:
        parent = p.parent.name
        m = re.search(r"__([a-zA-Z]+)$", parent)
        if not m:
            return None
        tag = m.group(1).lower()
        if tag == "healthy":
            return "Healthy"
        if tag == "n":
            return "Nitrogen"
        if tag == "k":
            return "Potassium"
        return None

    return None


# ============================================================
# SPLIT LOGIC (unique-hash groups; duplicates stay together)
# ============================================================
def choose_split_counts(n_unique: int) -> Tuple[int, int]:
    """
    Choose (n_val, n_test) based on UNIQUE images (hash groups) for a class.
    """
    n_val = min(VAL_PER_CLASS, max(MIN_VAL_PER_CLASS, int(round(n_unique * 0.1))))
    n_test = min(TEST_PER_CLASS, max(MIN_TEST_PER_CLASS, int(round(n_unique * 0.1))))

    if n_unique <= 2:
        return (0, 0)

    max_holdout = n_unique - 1
    if n_val + n_test > max_holdout:
        s = max_holdout / max(1, (n_val + n_test))
        n_val = int(math.floor(n_val * s))
        n_test = int(math.floor(n_test * s))

    return (max(0, n_val), max(0, n_test))

def sample_holdout_diverse(groups: List[HashGroup], k: int, rng: random.Random) -> List[HashGroup]:
    """
    Diversity-aware holdout sampling WITHOUT discarding training data.
    We sample UNIQUE hash-groups; all their occurrences follow into the chosen split.
    Strategy:
      - round-robin across sources, then across folders, based on occurrences.
    """
    if k <= 0 or not groups:
        return []

    # Derive source + folder label per group (majority over occurrences)
    def group_src_folder(g: HashGroup) -> Tuple[str, str]:
        src = Counter(o.source for o in g.occs).most_common(1)[0][0]
        fol = Counter(o.folder for o in g.occs).most_common(1)[0][0]
        return src, fol

    buckets: Dict[str, Dict[str, List[HashGroup]]] = {}  # src -> folder -> groups
    for g in groups:
        s, f = group_src_folder(g)
        buckets.setdefault(s, {}).setdefault(f, []).append(g)

    sources = list(buckets.keys())
    rng.shuffle(sources)
    for s in sources:
        folders = list(buckets[s].keys())
        rng.shuffle(folders)
        for f in folders:
            rng.shuffle(buckets[s][f])

    selected: List[HashGroup] = []
    src_idx = {s: 0 for s in sources}
    fol_idx: Dict[Tuple[str, str], int] = {}
    fols = {s: list(buckets[s].keys()) for s in sources}

    # round-robin source -> folder -> next item
    while len(selected) < k:
        progress = False
        for s in sources:
            if len(selected) >= k:
                break
            if not fols[s]:
                continue

            # cycle folders inside source
            for _ in range(len(fols[s])):
                f = fols[s][src_idx[s] % len(fols[s])]
                src_idx[s] += 1

                key = (s, f)
                if key not in fol_idx:
                    fol_idx[key] = 0

                i = fol_idx[key]
                if i < len(buckets[s][f]):
                    selected.append(buckets[s][f][i])
                    fol_idx[key] += 1
                    progress = True
                    break

        if not progress:
            # exhausted
            break

    return selected[:k]


# ============================================================
# PIPELINE
# ============================================================
def collect_sources() -> Dict[str, List[Path]]:
    found: Dict[str, List[Path]] = {c: [] for c in CLASSES}
    skipped = 0
    ignored = 0

    for root in SOURCE_ROOTS:
        if not root.exists():
            print(f"⚠️ Missing source root: {root}")
            continue

        for p in root.rglob("*"):
            if not is_image(p):
                continue
            if should_skip(p):
                skipped += 1
                continue

            label = infer_label_from_path(p)
            if label is None or label not in CLASS_SET:
                ignored += 1
                continue

            found[label].append(p)

    print("\n=== RAW COLLECTION COUNTS (Elite-6 only) ===")
    total = 0
    for c in CLASSES:
        n = len(found[c])
        total += n
        print(f"{c:10s}: {n}")
    print(f"Total labeled images: {total}")
    print(f"Skipped (explicit):   {skipped}")
    print(f"Ignored/unmapped:    {ignored}\n")
    return found


def cache_and_group(found: Dict[str, List[Path]]) -> Tuple[Dict[str, List[HashGroup]], Dict[str, List[Dict]]]:
    """
    Cache images to fixed size and group by content hash per class.
    IMPORTANT: Does NOT discard duplicates. It keeps all occurrences under one HashGroup.
    Returns:
      - groups_by_class: class -> [HashGroup...]
      - cross_class_dupes: list of {hash, class_a, class_b, rep_a, rep_b} warnings
    """
    safe_mkdir(CACHE_DIR)
    rng = random.Random(SEED)

    groups_by_class: Dict[str, List[HashGroup]] = {c: [] for c in CLASSES}
    cross_class_dupes: List[Dict] = []

    global_hash_owner: Dict[str, Tuple[str, str]] = {}  # hash -> (class, rep_cache_path)

    corrupt = 0

    print(f"=== BUILDING / REUSING CACHES: {CACHE_SIZES} ===")
    
    # Pre-create all directories to avoid race conditions in workers
    for cls in CLASSES:
        for size in CACHE_SIZES:
            c_dir = OUTPUT_DIR.parent / f"_common_cache_{size}" / cls
            safe_mkdir(c_dir)

    tasks = []
    # Prepare tasks
    for cls in CLASSES:
        for src_img in found[cls]:
            tasks.append((src_img, cls, OUTPUT_DIR, CACHE_SIZES, IMG_SIZE, JPEG_QUALITY))
            
    print(f"Processing {len(tasks)} images on CPU pool (all cores)...")

    # Execute
    # We use a large chunksize to reduce IPC overhead
    n_workers = os.cpu_count() or 4
    results = []
    
    with ProcessPoolExecutor(max_workers=n_workers) as executor:
        # submit all
        futures = {executor.submit(process_one_image_job, t): t for t in tasks}
        
        for f in tqdm(as_completed(futures), total=len(tasks), desc="Processing", unit="img"):
            res = f.result()
            if res:
                results.append(res)
            else:
                corrupt += 1

    # Reassemble results into groups
    h_to_group_by_cls = {c: {} for c in CLASSES} # temp map

    for res in results:
        h = res["h"]
        cls = res["cls"]
        primary_out_path = Path(res["primary_out_path"])
        orig_path = res["orig_path"]
        source = res["source"]
        folder = res["folder"]

        occ = Occurrence(orig_path=orig_path, source=source, folder=folder)
        
        # Access local map
        h_map = h_to_group_by_cls[cls]

        if h in h_map:
            h_map[h].occs.append(occ)
        else:
            h_map[h] = HashGroup(h=h, rep_cache_path=primary_out_path, occs=[occ])
            
            # Cross-class check logic (Global)
            if h in global_hash_owner:
                other_cls, other_rep = global_hash_owner[h]
                if other_cls != cls:
                    cross_class_dupes.append({
                        "hash": h,
                        "class_a": other_cls,
                        "class_b": cls,
                        "rep_a": other_rep,
                        "rep_b": str(primary_out_path),
                    })
            else:
                global_hash_owner[h] = (cls, str(primary_out_path))

    # Convert to final list format
    for cls in CLASSES:
         groups = list(h_to_group_by_cls[cls].values())
         rng.shuffle(groups)
         groups_by_class[cls] = groups

    print(f"\nCorrupt/unreadable skipped during cache: {corrupt}")

    print("\n=== UNIQUE HASH-GROUP COUNTS (per class) ===")
    for c in CLASSES:
        unique_n = len(groups_by_class[c])
        total_occ = sum(len(g.occs) for g in groups_by_class[c])
        print(f"{c:10s}: unique={unique_n}  total_occurrences={total_occ}")

    if cross_class_dupes:
        print(f"\n⚠️ WARNING: Found {len(cross_class_dupes)} cross-class identical images (possible label noise).")
        print("   These are NOT removed. They will be kept but can confuse training.")

    print()
    return groups_by_class, cross_class_dupes


def build_splits(groups_by_class: Dict[str, List[HashGroup]], cross_class_dupes: List[Dict]) -> None:
    print(f"=== BUILDING DATASET: {OUTPUT_DIR.name} ===")

    # Reset output split folders (keep cache)
    for split in ["train", "valid", "test"]:
        split_dir = OUTPUT_DIR / split
        if split_dir.exists():
            shutil.rmtree(split_dir)
        for cls in CLASSES:
            safe_mkdir(split_dir / cls)

    rng = random.Random(SEED)

    report = {
        "version": "v3_elite6_alltrain",
        "img_size": IMG_SIZE,
        "jpeg_quality": JPEG_QUALITY,
        "seed": SEED,
        "counts": {
            "unique_groups": {"train": {}, "valid": {}, "test": {}},
            "total_files": {"train": {}, "valid": {}, "test": {}},
        },
        "source_breakdown_total_files": {"train": {}, "valid": {}, "test": {}},
        "cross_class_identical_warnings": cross_class_dupes,
    }

    # Leakage guard: ensure same hash-group (within class) doesn't end up in multiple splits
    # (By construction we split on hash-groups, so this stays safe.)

    meta_rows = []

    for cls in CLASSES:
        groups = list(groups_by_class[cls])
        rng.shuffle(groups)

        n_unique = len(groups)
        n_val, n_test = choose_split_counts(n_unique)

        # Holdouts: choose unique groups (diverse), remaining -> train (ALL)
        val_groups = sample_holdout_diverse(groups, n_val, rng)
        val_hashes = {g.h for g in val_groups}
        remaining = [g for g in groups if g.h not in val_hashes]

        test_groups = sample_holdout_diverse(remaining, n_test, rng)
        test_hashes = {g.h for g in test_groups}
        train_groups = [g for g in remaining if g.h not in test_hashes]  # ALL remaining

        splits = {
            "train": train_groups,
            "valid": val_groups,
            "test": test_groups,
        }

        # Materialize: write ONE output file per occurrence (no discard),
        # using the group's representative cached image file as the link/copy source.
        for split_name, split_groups in splits.items():
            out_dir = OUTPUT_DIR / split_name / cls

            total_files = 0
            src_counts = Counter()

            idx = 0
            for g in split_groups:
                for occ in g.occs:
                    # unique name per occurrence
                    tag = sha1_text(occ.orig_path)[:10]
                    dst = out_dir / f"{idx:07d}_{tag}.jpg"
                    hardlink_or_copy(g.rep_cache_path, dst)

                    meta_rows.append({
                        "split": split_name,
                        "class": cls,
                        "output_path": str(dst),
                        "hash": g.h,
                        "source": occ.source,
                        "folder": occ.folder,
                        "orig_path": occ.orig_path,
                        "rep_cache_path": str(g.rep_cache_path),
                    })

                    total_files += 1
                    src_counts[occ.source] += 1
                    idx += 1

            report["counts"]["unique_groups"][split_name][cls] = len(split_groups)
            report["counts"]["total_files"][split_name][cls] = total_files
            report["source_breakdown_total_files"][split_name][cls] = dict(src_counts.most_common())

    safe_mkdir(OUTPUT_DIR)
    report_path = OUTPUT_DIR / "build_report.json"
    with report_path.open("w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    meta_path = OUTPUT_DIR / "meta.csv"
    with meta_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(meta_rows[0].keys()) if meta_rows else [])
        if meta_rows:
            writer.writeheader()
            writer.writerows(meta_rows)

    print("\n=== FINAL SPLIT COUNTS (unique groups / total files) ===")
    for split in ["train", "valid", "test"]:
        print(f"\n[{split.upper()}]")
        for cls in CLASSES:
            ug = report["counts"]["unique_groups"][split][cls]
            tf = report["counts"]["total_files"][split][cls]
            print(f"  {cls:10s}: unique={ug:<5d}  files={tf}")

    print(f"\n✅ Done. Dataset written to: {OUTPUT_DIR}")
    print(f"📄 Report: {report_path}")
    print(f"🧾 Meta CSV: {meta_path}")
    print(f"🧠 Cache kept at: {CACHE_DIR} (reruns are fast)")


def main() -> None:
    set_seed(SEED)

    print("====================================================")
    print("LeafBloom - Elite6 V3 ALL-TRAIN (PRODUCTION)")
    print(f" - {IMG_SIZE}x{IMG_SIZE} Dataset Build")
    print(f" - Multi-Cache: {CACHE_SIZES}")
    print(" - Elite-6 only + skip NitrogenDeficiencyImage")
    print(" - Train keeps ALL remaining images (no downsampling)")
    print(" - Exact duplicates are NOT removed; they are grouped so they stay in one split")
    print("====================================================\n")

    raw = collect_sources()
    groups_by_class, cross_class_dupes = cache_and_group(raw)
    build_splits(groups_by_class, cross_class_dupes)


if __name__ == "__main__":
    main()

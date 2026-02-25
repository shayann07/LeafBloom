"""
LeafBloom Elite-6 Training (PROD)
- Perfect class balance per epoch (exact, not probabilistic)
- Optional balance-by-source (domain diversity)
- Drops duplicates + drops cross-class identical hashes (label noise)
- Class-Balanced Loss (effective number of samples) + sampler (hardcore)
- AMP + Grad Checkpointing + EMA
- Auto batch size (fill VRAM safely)
"""

import argparse
import csv
import json
import math
import os
import random
import time
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Dict, List, Tuple, Optional, Iterable
from collections import defaultdict

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader, Sampler, WeightedRandomSampler

import timm
from timm.data import create_transform, resolve_model_data_config
from timm.data.mixup import Mixup
from timm.utils.model_ema import ModelEmaV2


DEFAULT_CLASSES = ["Calcium", "Healthy", "Nitrogen", "Phosphorus", "Potassium", "Sulphur"]


# ---------------------------
# Utils
# ---------------------------
def now_ts() -> str:
    return time.strftime("%Y%m%d_%H%M%S")


def set_seed(seed: int, deterministic: bool = False) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)
    os.environ["PYTHONHASHSEED"] = str(seed)

    if deterministic:
        torch.backends.cudnn.benchmark = False
        torch.backends.cudnn.deterministic = True
        try:
            torch.use_deterministic_algorithms(True)
        except Exception:
            pass
    else:
        torch.backends.cudnn.benchmark = True


def get_device() -> torch.device:
    return torch.device("cuda" if torch.cuda.is_available() else "cpu")


def cuda_mem_gb() -> float:
    if not torch.cuda.is_available():
        return 0.0
    return torch.cuda.max_memory_allocated() / (1024 ** 3)


# ---------------------------
# Meta loader (from your builder)
# meta.csv columns:
# split,class,output_path,hash,source,folder,orig_path,rep_cache_path
# ---------------------------
def load_meta_rows(meta_csv: Path) -> List[dict]:
    if not meta_csv.exists():
        raise FileNotFoundError(f"meta.csv not found: {meta_csv}")

    rows = []
    with meta_csv.open("r", newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for r in reader:
            # Normalize fields we use
            r["split"] = r["split"].strip().lower()
            r["class"] = r["class"].strip()
            r["hash"] = r["hash"].strip()
            r["source"] = r.get("source", "UNK").strip().upper() or "UNK"
            r["output_path"] = r["output_path"].strip()
            rows.append(r)
    return rows


def load_cross_class_hashes(build_report_json: Path) -> set:
    if not build_report_json.exists():
        return set()

    data = json.loads(build_report_json.read_text(encoding="utf-8"))
    warnings = data.get("cross_class_identical_warnings", [])
    return {w["hash"] for w in warnings if "hash" in w}


def filter_rows(
    rows: List[dict],
    classes: List[str],
    drop_exact_dupes: bool,
    drop_cross_class: bool,
    cross_class_hashes: set,
) -> List[dict]:
    classes_set = set(classes)

    # Keep only target classes
    rows = [r for r in rows if r["class"] in classes_set]

    if drop_cross_class and cross_class_hashes:
        rows = [r for r in rows if r["hash"] not in cross_class_hashes]

    if drop_exact_dupes:
        # Dedupe by hash globally (also guards leakage across splits if it ever happens)
        seen = set()
        out = []
        for r in rows:
            h = r["hash"]
            if h in seen:
                continue
            seen.add(h)
            out.append(r)
        rows = out

    return rows


def split_rows(rows: List[dict]) -> Dict[str, List[dict]]:
    out = {"train": [], "valid": [], "test": []}
    for r in rows:
        if r["split"] == "train":
            out["train"].append(r)
        elif r["split"] in ("valid", "val"):
            out["valid"].append(r)
        elif r["split"] == "test":
            out["test"].append(r)
    return out


# ---------------------------
# Dataset (paths from meta.csv)
# ---------------------------
class PathDataset(Dataset):
    def __init__(self, samples: List[Tuple[str, int, str]], transform):
        """
        samples: list of (path, label_idx, source)
        """
        self.samples = samples
        self.transform = transform

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx: int):
        path, y, _src = self.samples[idx]
        from PIL import Image  # local import to avoid PIL import issues on some setups
        img = Image.open(path).convert("RGB")
        x = self.transform(img)
        return x, y


# ---------------------------
# Perfect balance sampler (optionally source-balanced)
# ---------------------------
class PerfectBalanceSampler(Sampler[int]):
    """
    Exactly N samples per class per epoch (and optionally balanced across sources within each class).
    Also rotates through majority classes across epochs so you're not "discarding" data.
    """
    def __init__(
        self,
        labels: List[int],
        sources: List[str],
        num_classes: int,
        samples_per_class: int,
        balance_by_source: bool,
        seed: int = 42,
    ):
        self.labels = labels
        self.sources = sources
        self.num_classes = num_classes
        self.samples_per_class = samples_per_class
        self.balance_by_source = balance_by_source
        self.seed = seed
        self.epoch = 0

        # Build lookup: class -> source -> indices
        self.cls_src_to_idx = defaultdict(lambda: defaultdict(list))
        self.cls_to_idx = defaultdict(list)
        for i, (y, s) in enumerate(zip(labels, sources)):
            self.cls_to_idx[y].append(i)
            self.cls_src_to_idx[y][s].append(i)

        # Sanity
        for c in range(num_classes):
            if len(self.cls_to_idx[c]) == 0:
                raise ValueError(f"Class {c} has 0 samples after filtering. Can't balance.")

    def set_epoch(self, epoch: int):
        self.epoch = epoch

    def __len__(self) -> int:
        return self.samples_per_class * self.num_classes

    def _pick_from_pool(self, pool: List[int], k: int, rng: random.Random, offset: int) -> List[int]:
        if len(pool) == 0 or k <= 0:
            return []
        # shuffle deterministically per epoch
        pool = pool.copy()
        rng.shuffle(pool)

        if len(pool) >= k:
            # rotate so we cycle through majority sets across epochs
            start = offset % len(pool)
            picked = []
            # wrap-around slice
            picked.extend(pool[start:min(len(pool), start + k)])
            if len(picked) < k:
                picked.extend(pool[0:(k - len(picked))])
            return picked

        # minority class: oversample by cycling (still shuffled)
        picked = []
        while len(picked) < k:
            picked.extend(pool)
        return picked[:k]

    def __iter__(self) -> Iterable[int]:
        # One RNG for this epoch
        rng = random.Random(self.seed + 1000 * self.epoch)

        per_class = self.samples_per_class
        # Build per-class selected index lists of length per_class
        chosen_by_class = {}

        for c in range(self.num_classes):
            if not self.balance_by_source:
                pool = self.cls_to_idx[c]
                # offset rotates through all samples
                offset = self.epoch * per_class
                chosen_by_class[c] = self._pick_from_pool(pool, per_class, rng, offset)
            else:
                src_map = self.cls_src_to_idx[c]
                srcs = sorted(list(src_map.keys()))
                n_src = len(srcs)

                base = per_class // n_src
                rem = per_class % n_src

                picked_all = []
                for j, src in enumerate(srcs):
                    take = base + (1 if j < rem else 0)
                    pool = src_map[src]
                    # source-level rotation
                    offset = self.epoch * max(1, take)
                    # mix seed per (class,source)
                    local_rng = random.Random(rng.randint(0, 10**9) ^ hash((c, src, self.epoch)))
                    picked_all.extend(self._pick_from_pool(pool, take, local_rng, offset))

                # safety
                if len(picked_all) != per_class:
                    picked_all = picked_all[:per_class]
                chosen_by_class[c] = picked_all

        # Interleave for nice per-batch balance (best if batch_size % num_classes == 0)
        for i in range(per_class):
            for c in range(self.num_classes):
                yield chosen_by_class[c][i]


# ---------------------------
# Class-Balanced weights (Cui et al. "effective number")
# ---------------------------
def class_balanced_weights(counts: List[int], beta: float = 0.9999) -> torch.Tensor:
    counts = np.array(counts, dtype=np.float64)
    eff_num = 1.0 - np.power(beta, counts)
    w = (1.0 - beta) / np.maximum(eff_num, 1e-12)
    # normalize to num_classes
    w = w / w.sum() * len(counts)
    return torch.tensor(w, dtype=torch.float32)


class WeightedSoftCE(nn.Module):
    """
    Cross-entropy that supports:
    - hard labels (LongTensor)
    - soft labels (mixup/cutmix)
    with optional class weights.
    """
    def __init__(self, class_w: Optional[torch.Tensor] = None):
        super().__init__()
        if class_w is not None:
            self.register_buffer("class_w", class_w)
        else:
            self.class_w = None

    def forward(self, logits: torch.Tensor, target: torch.Tensor) -> torch.Tensor:
        logp = F.log_softmax(logits, dim=1)

        if target.dtype in (torch.int64, torch.long):
            # hard labels
            return F.nll_loss(logp, target, weight=self.class_w, reduction="mean")

        # soft labels: target shape (B, C)
        if self.class_w is None:
            loss = -(target * logp).sum(dim=1)
            return loss.mean()

        w = self.class_w.unsqueeze(0)  # (1, C)
        # weighted soft CE (normalize by weighted target mass to keep loss scale stable)
        num = -(target * w * logp).sum(dim=1)
        den = (target * w).sum(dim=1).clamp_min(1e-12)
        return (num / den).mean()


# ---------------------------
# Auto batch finder (use your 6GB)
# ---------------------------
def find_max_batch_size(
    model: nn.Module,
    num_classes: int,
    img_size: int,
    device: torch.device,
    use_amp: bool,
    max_try: int = 256,
) -> int:
    if device.type != "cuda":
        return 16

    # candidate sizes (fast)
    candidates = [16, 24, 32, 40, 48, 56, 64, 72, 80, 96, 112, 128, 160, 192, 224, 256]
    candidates = [b for b in candidates if b <= max_try]

    best = candidates[0]
    model.train()
    crit = nn.CrossEntropyLoss().to(device)
    opt = optim.SGD(model.parameters(), lr=1e-3)

    for b in candidates:
        try:
            torch.cuda.empty_cache()
            torch.cuda.reset_peak_memory_stats()

            x = torch.randn(b, 3, img_size, img_size, device=device)
            y = torch.randint(0, num_classes, (b,), device=device)

            model.zero_grad(set_to_none=True)
            with torch.cuda.amp.autocast(enabled=use_amp):
                out = model(x)
                loss = crit(out, y)

            loss.backward()
            # opt.step()  <-- REMOVED to avoid weight mutation

            best = b
        except RuntimeError as e:
            if "out of memory" in str(e).lower():
                torch.cuda.empty_cache()
                break
            raise
    return best


# ---------------------------
# Eval
# ---------------------------
@torch.no_grad()
def evaluate(model: nn.Module, loader: DataLoader, device: torch.device, use_amp: bool) -> Tuple[float, float]:
    model.eval()
    crit = nn.CrossEntropyLoss()
    total, correct, loss_sum = 0, 0, 0.0

    for x, y in loader:
        x = x.to(device, non_blocking=True)
        y = y.to(device, non_blocking=True)
        with torch.cuda.amp.autocast(enabled=use_amp):
            logits = model(x)
            loss = crit(logits, y)
        loss_sum += loss.item() * x.size(0)
        pred = logits.argmax(dim=1)
        correct += (pred == y).sum().item()
        total += y.size(0)

    return loss_sum / max(1, total), correct / max(1, total)


# ---------------------------
# Config
# ---------------------------
@dataclass
class TrainCfg:
    data_dir: str
    out_dir: str
    model_name: str = "convnextv2_base.fcmae_ft_in22k_in1k"
    classes: List[str] = None

    img_size: int = 256
    epochs: int = 60
    lr: float = 2e-4
    weight_decay: float = 0.05
    workers: int = 4

    batch_size: int = 0  # 0 => auto
    accum_steps: int = 1
    grad_clip: float = 1.0

    amp: bool = True
    grad_checkpointing: bool = True
    ema_decay: float = 0.9999

    # Perfect balance controls
    samples_per_class: int = 600     # per epoch, per class (balanced)
    balance_by_source: bool = True   # domain diversity

    # Duplicate handling
    drop_exact_dupes: bool = True
    drop_cross_class_dupes: bool = True

    # Mixup/CutMix
    use_mixup: bool = False
    mixup_alpha: float = 0.2
    cutmix_alpha: float = 1.0
    mixup_prob: float = 0.8
    mixup_switch_prob: float = 0.5
    label_smoothing: float = 0.05

    # Class-balanced loss
    cb_beta: float = 0.9999

    # Scheduler
    sched: str = "onecycle"  # onecycle | cosine
    warmup_epochs: int = 5

    # Early stop
    patience: int = 15
    min_delta: float = 1e-4

    seed: int = 42
    deterministic: bool = False

    resume: str = ""

    # Sampler mode
    sampler: str = "perfect"  # perfect | weighted


def build_scheduler(cfg: TrainCfg, optimizer: optim.Optimizer, steps_per_epoch: int):
    # Handle differential max_lr if optimizer has multiple groups
    if len(optimizer.param_groups) > 1:
        # Assuming group 0 is body, group 1 is head (from main)
        # But safer to just read from the groups themselves if they follow the ratio
        # However, OneCycleLR expects explicit max_lrs
        max_lr = [pg['lr'] for pg in optimizer.param_groups]
    else:
        max_lr = cfg.lr

    if cfg.sched == "onecycle":
        return optim.lr_scheduler.OneCycleLR(
            optimizer,
            max_lr=max_lr,
            epochs=cfg.epochs,
            steps_per_epoch=steps_per_epoch,
            pct_start=0.2,
            div_factor=25.0,
            final_div_factor=1e4,
        ), "iter"

    if cfg.sched == "cosine":
        return optim.lr_scheduler.CosineAnnealingLR(
            optimizer,
            T_max=max(1, cfg.epochs - cfg.warmup_epochs),
            eta_min=cfg.lr * 1e-2,
        ), "epoch"

    raise ValueError(cfg.sched)


def maybe_warmup_lr(cfg: TrainCfg, optimizer: optim.Optimizer, epoch0: int):
    if cfg.sched != "cosine":
        return
    if epoch0 < cfg.warmup_epochs:
        frac = (epoch0 + 1) / max(1, cfg.warmup_epochs)
        lr = cfg.lr * frac
        for pg in optimizer.param_groups:
            pg["lr"] = lr


def save_ckpt(path: Path, payload: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    torch.save(payload, str(path))


# ---------------------------
# Main
# ---------------------------
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data-dir", type=str, default=r"D:\Work\AndroidStudioProjects\FYP\LeafBloom\ml\nutrient_deficiency\dataset_v3_elite6_384px")
    ap.add_argument("--out-dir", type=str, default="training_output_elite6_384_prod")
    ap.add_argument("--model", type=str, default="convnextv2_base.fcmae_ft_in22k_in1k_384")

    ap.add_argument("--img-size", type=int, default=384)
    ap.add_argument("--epochs", type=int, default=60)
    ap.add_argument("--lr", type=float, default=2e-4)
    ap.add_argument("--weight-decay", type=float, default=0.05)
    ap.add_argument("--workers", type=int, default=4)

    ap.add_argument("--batch-size", type=int, default=0, help="0=auto-find max that fits your GPU")
    ap.add_argument("--accum-steps", type=int, default=1)
    ap.add_argument("--grad-clip", type=float, default=1.0)

    ap.add_argument("--no-amp", action="store_true")
    ap.add_argument("--no-grad-checkpointing", action="store_true")
    ap.add_argument("--ema-decay", type=float, default=0.9999)

    ap.add_argument("--samples-per-class", type=int, default=600, help="perfect balance target per class per epoch")
    ap.add_argument("--no-balance-by-source", action="store_true")
    ap.add_argument("--sampler", choices=["perfect", "weighted"], default="perfect")

    ap.add_argument("--keep-dupes", action="store_true")
    ap.add_argument("--keep-cross-class-dupes", action="store_true")

    ap.add_argument("--no-mixup", action="store_true", help="Disable Mixup (Enabled by default)")
    ap.add_argument("--mixup-alpha", type=float, default=0.2)
    ap.add_argument("--cutmix-alpha", type=float, default=1.0)
    ap.add_argument("--mixup-prob", type=float, default=0.8)
    ap.add_argument("--mixup-switch-prob", type=float, default=0.5)
    ap.add_argument("--label-smoothing", type=float, default=0.05)

    ap.add_argument("--cb-beta", type=float, default=0.9999)

    ap.add_argument("--sched", choices=["onecycle", "cosine"], default="onecycle")
    ap.add_argument("--warmup-epochs", type=int, default=5)

    ap.add_argument("--patience", type=int, default=15)
    ap.add_argument("--min-delta", type=float, default=1e-4)

    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--deterministic", action="store_true")
    ap.add_argument("--resume", type=str, default="")

    args = ap.parse_args()

    cfg = TrainCfg(
        data_dir=args.data_dir,
        out_dir=args.out_dir,
        model_name=args.model,
        classes=DEFAULT_CLASSES,
        img_size=args.img_size,
        epochs=args.epochs,
        lr=args.lr,
        weight_decay=args.weight_decay,
        workers=args.workers,
        batch_size=args.batch_size,
        accum_steps=max(1, args.accum_steps),
        grad_clip=args.grad_clip,
        amp=not args.no_amp,
        grad_checkpointing=not args.no_grad_checkpointing,
        ema_decay=args.ema_decay,
        samples_per_class=args.samples_per_class,
        balance_by_source=not args.no_balance_by_source,
        drop_exact_dupes=not args.keep_dupes,
        drop_cross_class_dupes=not args.keep_cross_class_dupes,
        use_mixup=not args.no_mixup,
        mixup_alpha=args.mixup_alpha,
        cutmix_alpha=args.cutmix_alpha,
        mixup_prob=args.mixup_prob,
        mixup_switch_prob=args.mixup_switch_prob,
        label_smoothing=args.label_smoothing,
        cb_beta=args.cb_beta,
        sched=args.sched,
        warmup_epochs=args.warmup_epochs,
        patience=args.patience,
        min_delta=args.min_delta,
        seed=args.seed,
        deterministic=args.deterministic,
        resume=args.resume,
        sampler=args.sampler,
    )

    device = get_device()
    set_seed(cfg.seed, deterministic=cfg.deterministic)

    if device.type == "cuda":
        torch.backends.cuda.matmul.allow_tf32 = True
        try:
            torch.set_float32_matmul_precision("high")
        except Exception:
            pass

    data_dir = Path(cfg.data_dir)
    meta_csv = data_dir / "meta.csv"
    report_json = data_dir / "build_report.json"

    rows = load_meta_rows(meta_csv)
    cross_hashes = load_cross_class_hashes(report_json) if cfg.drop_cross_class_dupes else set()

    rows_f = filter_rows(
        rows,
        classes=cfg.classes,
        drop_exact_dupes=cfg.drop_exact_dupes,
        drop_cross_class=cfg.drop_cross_class_dupes,
        cross_class_hashes=cross_hashes,
    )
    splits = split_rows(rows_f)

    # Build class index mapping
    class_to_idx = {c: i for i, c in enumerate(cfg.classes)}

    def build_samples(split_name: str) -> Tuple[List[Tuple[str, int, str]], List[int], List[str]]:
        srows = splits[split_name]
        samples = []
        labels = []
        sources = []
        for r in srows:
            p = r["output_path"]
            y = class_to_idx[r["class"]]
            src = r.get("source", "UNK")
            if not os.path.exists(p):
                continue
            samples.append((p, y, src))
            labels.append(y)
            sources.append(src)
        return samples, labels, sources

    train_samples, train_labels, train_sources = build_samples("train")
    val_samples, _, _ = build_samples("valid")
    test_samples, _, _ = build_samples("test")

    num_classes = len(cfg.classes)
    if len(train_samples) == 0 or len(val_samples) == 0 or len(test_samples) == 0:
        raise RuntimeError("One of your splits ended up empty after filtering. Check paths and filters.")

    # Timm transforms
    tmp = timm.create_model(cfg.model_name, pretrained=True, num_classes=1)
    data_cfg = resolve_model_data_config(tmp)
    if "input_size" in data_cfg and len(data_cfg["input_size"]) == 3:
        data_cfg["input_size"] = (3, cfg.img_size, cfg.img_size)
    train_tf = create_transform(**data_cfg, is_training=True)
    eval_tf = create_transform(**data_cfg, is_training=False)

    train_ds = PathDataset(train_samples, transform=train_tf)
    val_ds = PathDataset(val_samples, transform=eval_tf)
    test_ds = PathDataset(test_samples, transform=eval_tf)

    # Model
    model = timm.create_model(cfg.model_name, pretrained=True, num_classes=num_classes)
    if cfg.grad_checkpointing and hasattr(model, "set_grad_checkpointing"):
        model.set_grad_checkpointing(enable=True)
    model.to(device)

    # Auto batch size (use VRAM)
    use_amp = cfg.amp and device.type == "cuda"
    # Auto batch size (use VRAM) + guarantee perfect balance
    use_amp = cfg.amp and device.type == "cuda"
    epoch_size = cfg.samples_per_class * num_classes

    if cfg.batch_size == 0:
        bs0 = find_max_batch_size(model, num_classes, cfg.img_size, device, use_amp=use_amp, max_try=256)

        # pick the largest <= bs0 that:
        # 1) divides the epoch_size (no drop_last truncation)
        # 2) is multiple of num_classes (perfect per-batch balance)
        best = None
        for b in range(bs0, 0, -1):
            if (epoch_size % b == 0) and (b % num_classes == 0):
                best = b
                break
        if best is None:
            # fallback: at least ensure epoch_size divisible
            for b in range(bs0, 0, -1):
                if epoch_size % b == 0:
                    best = b
                    break

        cfg.batch_size = max(1, best)
        print(f"✅ Auto batch-size picked: {cfg.batch_size} (epoch_size={epoch_size}, classes={num_classes})")

    # Loss weights (class-balanced)
    per_class_counts = [0] * num_classes
    for y in train_labels:
        per_class_counts[y] += 1
    cb_w = class_balanced_weights(per_class_counts, beta=cfg.cb_beta).to(device)
    train_criterion = WeightedSoftCE(class_w=cb_w)

    # Mixup
    mixup_fn = None
    if cfg.use_mixup:
        mixup_fn = Mixup(
            mixup_alpha=cfg.mixup_alpha,
            cutmix_alpha=cfg.cutmix_alpha,
            prob=cfg.mixup_prob,
            switch_prob=cfg.mixup_switch_prob,
            mode="batch",
            label_smoothing=cfg.label_smoothing,
            num_classes=num_classes,
        )

    # Sampler
    if cfg.sampler == "perfect":
        sampler = PerfectBalanceSampler(
            labels=train_labels,
            sources=train_sources,
            num_classes=num_classes,
            samples_per_class=cfg.samples_per_class,
            balance_by_source=cfg.balance_by_source,
            seed=cfg.seed,
        )
    else:
        # WeightedRandomSampler (probabilistic, not exact)
        class_counts = np.bincount(np.array(train_labels), minlength=num_classes)
        inv = 1.0 / np.maximum(class_counts, 1)
        weights = [inv[y] for y in train_labels]
        sampler = WeightedRandomSampler(weights=weights, num_samples=cfg.samples_per_class * num_classes, replacement=True)

    # Loaders
    common = dict(
        num_workers=cfg.workers,
        pin_memory=(device.type == "cuda"),
        persistent_workers=(cfg.workers > 0),
    )

    train_loader = DataLoader(
        train_ds,
        batch_size=cfg.batch_size,
        sampler=sampler,
        shuffle=False,
        drop_last=True,
        **common,
    )
    val_loader = DataLoader(val_ds, batch_size=cfg.batch_size, shuffle=False, drop_last=False, **common)
    test_loader = DataLoader(test_ds, batch_size=cfg.batch_size, shuffle=False, drop_last=False, **common)

    # Separate head and backbone params for better fine-tuning
    head_params = []
    backbone_params = []
    head_names = []
    
    # Identify head layers (ConvNeXt uses 'head.fc')
    for name, param in model.named_parameters():
        if "head" in name:
            head_params.append(param)
            head_names.append(name)
        else:
            backbone_params.append(param)
            
    # Optimizer with differential LR (10x for head)
    optimizer = optim.AdamW([
        {'params': backbone_params, 'lr': cfg.lr},
        {'params': head_params, 'lr': cfg.lr * 10.0}
    ], weight_decay=cfg.weight_decay)
    
    scheduler, sched_step = build_scheduler(cfg, optimizer, steps_per_epoch=len(train_loader))

    scaler = torch.cuda.amp.GradScaler(enabled=use_amp)
    ema = ModelEmaV2(model, decay=cfg.ema_decay, device=device)

    # Output dirs
    out_root = Path(cfg.out_dir)
    run_dir = out_root / f"run_{now_ts()}"
    run_dir.mkdir(parents=True, exist_ok=True)
    (run_dir / "config.json").write_text(json.dumps(asdict(cfg), indent=2), encoding="utf-8")

    best_path = run_dir / "best.pt"
    last_path = run_dir / "last.pt"

    # Resume
    start_epoch = 1
    best_val_acc = 0.0
    best_epoch = 0
    patience_ctr = 0

    if cfg.resume:
        ckpt = torch.load(cfg.resume, map_location=device)
        model.load_state_dict(ckpt["model"])
        ema.module.load_state_dict(ckpt["ema"])
        optimizer.load_state_dict(ckpt["optim"])
        if ckpt.get("sched") is not None:
            scheduler.load_state_dict(ckpt["sched"])
        if ckpt.get("scaler") is not None:
            scaler.load_state_dict(ckpt["scaler"])
        start_epoch = int(ckpt.get("epoch", 0)) + 1
        best_val_acc = float(ckpt.get("best_val_acc", 0.0))
        best_epoch = int(ckpt.get("best_epoch", 0))
        print(f"✅ Resumed from {cfg.resume} @ epoch {start_epoch-1} (best={best_val_acc:.4f})")

    print("=" * 80)
    print("LeafBloom | Elite-6 Training (Perfect Balance + Class-Balanced Loss)")
    print(f"Device: {device}")
    print(f"Model : {cfg.model_name}")
    print(f"Data  : {cfg.data_dir}")
    print(f"Run   : {run_dir}")
    print(f"IMG   : {cfg.img_size} | BS={cfg.batch_size} | AMP={use_amp} | GCkpt={cfg.grad_checkpointing}")
    print(f"Sampler: {cfg.sampler} | per-class/epoch={cfg.samples_per_class} | by-source={cfg.balance_by_source}")
    print(f"Dedupe: exact={cfg.drop_exact_dupes} | cross-class={cfg.drop_cross_class_dupes} (hashes={len(cross_hashes)})")
    print("Train counts after filtering:", per_class_counts)
    print("CB weights:", [round(x, 4) for x in cb_w.detach().cpu().tolist()])
    print("=" * 80)

    from tqdm import tqdm

    for epoch in range(start_epoch, cfg.epochs + 1):
        model.train()
        t0 = time.time()

        if cfg.sched == "cosine":
            maybe_warmup_lr(cfg, optimizer, epoch0=epoch - 1)

        # set epoch for perfect sampler
        if hasattr(train_loader.sampler, "set_epoch"):
            train_loader.sampler.set_epoch(epoch)

        total, correct, loss_sum = 0, 0, 0.0
        optimizer.zero_grad(set_to_none=True)

        torch.cuda.reset_peak_memory_stats() if device.type == "cuda" else None

        pbar = tqdm(train_loader, desc=f"Epoch {epoch:03d}/{cfg.epochs}", dynamic_ncols=True)
        for step, (x, y) in enumerate(pbar, start=1):
            x = x.to(device, non_blocking=True)
            y = y.to(device, non_blocking=True)

            if mixup_fn is not None:
                x, y_mix = mixup_fn(x, y)
            else:
                y_mix = y

            with torch.cuda.amp.autocast(enabled=use_amp):
                logits = model(x)
                loss = train_criterion(logits, y_mix) / cfg.accum_steps

            scaler.scale(loss).backward()

            # accuracy vs hard labels (still meaningful even with mixup)
            pred = logits.argmax(dim=1)
            correct += (pred == y).sum().item()
            total += y.size(0)
            loss_sum += loss.item() * x.size(0) * cfg.accum_steps

            if step % cfg.accum_steps == 0:
                if cfg.grad_clip and cfg.grad_clip > 0:
                    scaler.unscale_(optimizer)
                    nn.utils.clip_grad_norm_(model.parameters(), cfg.grad_clip)

                scaler.step(optimizer)
                scaler.update()
                optimizer.zero_grad(set_to_none=True)

                if sched_step == "iter":
                    scheduler.step()

                ema.update(model)

            lr_now = optimizer.param_groups[0]["lr"]
            pbar.set_postfix({
                "loss": f"{(loss_sum/max(1,total)):.4f}",
                "acc": f"{(correct/max(1,total)):.4f}",
                "lr": f"{lr_now:.2e}",
                "vramGB": f"{cuda_mem_gb():.2f}" if device.type == "cuda" else "0.00",
            })

        if sched_step == "epoch" and cfg.sched == "cosine":
            if epoch > cfg.warmup_epochs:
                scheduler.step()

        train_loss = loss_sum / max(1, total)
        train_acc = correct / max(1, total)
        
        # Evaluate RAW model
        val_loss_raw, val_acc_raw = evaluate(model, val_loader, device, use_amp=use_amp)
        # Evaluate EMA model
        val_loss_ema, val_acc_ema = evaluate(ema.module, val_loader, device, use_amp=use_amp)

        dt = time.time() - t0
        lr_now = optimizer.param_groups[0]["lr"]
        peak_gb = cuda_mem_gb()

        print(
            f"Epoch {epoch:03d}/{cfg.epochs} | "
            f"train_loss={train_loss:.4f} train_acc={train_acc:.4f} | "
            f"val_raw={val_acc_raw:.4f} val_ema={val_acc_ema:.4f} | "
            f"lr={lr_now:.2e} | peakVRAM={peak_gb:.2f}GB | {dt:.1f}s"
        )
        
        # Use EMA for best checkpointing, but fall back to raw if EMA is lagging badly
        # UPDATE: EMA takes too long to catch up. We will track RAW for patience/best-saving.
        # But we still save EMA weights in the checkpoint for inference usage.
        val_acc_metric = val_acc_raw 
        
        save_ckpt(
            last_path,
            {
                "epoch": epoch,
                "model": model.state_dict(),
                "ema": ema.module.state_dict(),
                "optim": optimizer.state_dict(),
                "sched": scheduler.state_dict() if scheduler is not None else None,
                "scaler": scaler.state_dict() if scaler is not None else None,
                "best_val_acc": best_val_acc,
                "best_epoch": best_epoch,
                "classes": cfg.classes,
                "cfg": asdict(cfg),
            },
        )

        if (val_acc_metric - best_val_acc) > cfg.min_delta:
            best_val_acc = val_acc_metric
            best_epoch = epoch
            patience_ctr = 0
            save_ckpt(
                best_path,
                {
                    "epoch": epoch,
                    "model": model.state_dict(),
                    "ema": ema.module.state_dict(),
                    "optim": optimizer.state_dict(),
                    "sched": scheduler.state_dict() if scheduler is not None else None,
                    "scaler": scaler.state_dict() if scaler is not None else None,
                    "best_val_acc": best_val_acc,
                    "best_epoch": best_epoch,
                    "classes": cfg.classes,
                    "cfg": asdict(cfg),
                },
            )
            print(f"🔥 New best val_acc={best_val_acc:.4f} (Raw/EMA) @ epoch {epoch}")
        else:
            patience_ctr += 1
            if patience_ctr >= cfg.patience:
                print(f"🛑 Early stopping. Best={best_val_acc:.4f} @ epoch {best_epoch}")
                break

    # Final eval (best EMA)
    best = torch.load(best_path, map_location=device)
    ema.module.load_state_dict(best["ema"])

    v_loss, v_acc = evaluate(ema.module, val_loader, device, use_amp=use_amp)
    t_loss, t_acc = evaluate(ema.module, test_loader, device, use_amp=use_amp)

    print("=" * 80)
    print(f"✅ Best epoch: {best_epoch} | Best val_acc: {best_val_acc:.4f}")
    print(f"📌 Final (EMA) Val : loss={v_loss:.4f} acc={v_acc:.4f}")
    print(f"📌 Final (EMA) Test: loss={t_loss:.4f} acc={t_acc:.4f}")
    print(f"Artifacts: {run_dir}")
    print("=" * 80)


if __name__ == "__main__":
    main()

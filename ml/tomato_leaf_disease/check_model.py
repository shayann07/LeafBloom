import torch
import sys

def check_model(path):
    print(f"Checking {path}")
    try:
        ckpt = torch.load(path, map_location='cpu')
        state_dict = ckpt.get('state_dict', ckpt.get('model_state_dict', ckpt))
        for k in state_dict.keys():
            if 'fc' in k or 'classifier' in k:
                print(f"Layer {k}: {state_dict[k].shape}")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == '__main__':
    check_model(sys.argv[1])

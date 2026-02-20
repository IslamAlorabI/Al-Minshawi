import os
from PIL import Image

input_file = '/Users/islamalorabi/Downloads/Al-Minshawi-Logo(3).png'
res_dir = '/Users/islamalorabi/Documents/ASProjects/Al-Minshawi/app/src/main/res'

if not os.path.exists(input_file):
    print("Input file not found!")
    exit(1)

img = Image.open(input_file).convert("RGBA")

r, g, b, a = img.split()
black = Image.new("L", img.size, 0)
mono_img = Image.merge("RGBA", (black, black, black, a))

densities = {
    'mipmap-mdpi': 108,
    'mipmap-hdpi': 162,
    'mipmap-xhdpi': 216,
    'mipmap-xxhdpi': 324,
    'mipmap-xxxhdpi': 432
}

for folder, size in densities.items():
    folder_path = os.path.join(res_dir, folder)
    os.makedirs(folder_path, exist_ok=True)
    
    safe_zone = int(size * (72/108) * 0.95)
    
    w, h = mono_img.size
    ratio = min(safe_zone / w, safe_zone / h)
    new_w, new_h = int(w * ratio), int(h * ratio)
    
    resized_logo = mono_img.resize((new_w, new_h), Image.Resampling.LANCZOS)
    
    canvas = Image.new("RGBA", (size, size), (0,0,0,0))
    
    offset_x = (size - new_w) // 2
    offset_y = (size - new_h) // 2
    canvas.paste(resized_logo, (offset_x, offset_y), resized_logo)
    
    out_path = os.path.join(folder_path, "ic_launcher_monochrome.png")
    canvas.save(out_path, "PNG")
    print(f"Created {out_path}")

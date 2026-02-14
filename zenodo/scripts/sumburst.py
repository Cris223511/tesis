import matplotlib.pyplot as plt
import numpy as np
from matplotlib.patches import Wedge, Circle

metrics_data = [
    {'name': 'ACC', 'count': 11, 'pct': 68.75, 'refs': ['[1]', '[2]', '[4]', '[6]', '[10]', '[22]', '[25]', '[26]', '[28]', '[32]', '[41]']},
    {'name': 'F1', 'count': 6, 'pct': 37.5, 'refs': ['[2]', '[4]', '[22]', '[25]', '[28]', '[31]']},
    {'name': 'Recall', 'count': 6, 'pct': 37.5, 'refs': ['[1]', '[2]', '[6]', '[22]', '[28]', '[41]']},
    {'name': 'Precision', 'count': 5, 'pct': 31.25, 'refs': ['[2]', '[6]', '[22]', '[28]', '[35]']},
    {'name': 'AUC', 'count': 2, 'pct': 12.5, 'refs': ['[22]', '[41]']},
    {'name': 'UAR', 'count': 1, 'pct': 6.25, 'refs': ['[1]']},
    {'name': 'Sens/Spec', 'count': 1, 'pct': 6.25, 'refs': ['[41]']},
]

color_light = '#F5E6D3'
color_dark = '#E8B649'

fig, ax = plt.subplots(figsize=(12, 12))
ax.set_aspect('equal')

total = sum([m['count'] for m in metrics_data])
start_angle = 90

for metric in metrics_data:
    angle_size = (metric['count'] / total) * 360
    refs_per_angle = angle_size / len(metric['refs'])

    for i, ref in enumerate(metric['refs']):
        ref_start = start_angle + (i * refs_per_angle)
        ref_end = ref_start + refs_per_angle
        color = color_dark if i % 2 == 0 else color_light

        wedge = Wedge((0, 0), 1.0, ref_start, ref_end, width=0.20,
                     facecolor=color, edgecolor='white', linewidth=1.5)
        ax.add_patch(wedge)

        mid_angle = np.radians(ref_start + refs_per_angle / 2)
        r = 0.93
        x, y = r * np.cos(mid_angle), r * np.sin(mid_angle)

        rotation = (ref_start + refs_per_angle / 2 - 90) % 360
        if rotation > 90 and rotation < 270:
            rotation += 180

        ax.text(x, y, f"{ref}\n{metric['pct']:.2f}%", ha='center', va='center',
               fontsize=12, rotation=rotation, weight='bold', color='black')

    color = color_dark if metrics_data.index(metric) % 2 == 0 else color_light
    wedge = Wedge((0, 0), 0.80, start_angle, start_angle + angle_size, width=0.20,
                 facecolor=color, edgecolor='white', linewidth=2)
    ax.add_patch(wedge)

    mid_angle = np.radians(start_angle + angle_size / 2)
    r = 0.72
    x, y = r * np.cos(mid_angle), r * np.sin(mid_angle)
    ax.text(x, y, metric['name'].upper(), ha='center', va='center',
           fontsize=12, weight='bold', color='black')

    color = color_light if metrics_data.index(metric) % 2 == 0 else color_dark
    wedge = Wedge((0, 0), 0.60, start_angle, start_angle + angle_size, width=0.20,
                 facecolor=color, edgecolor='white', linewidth=2)
    ax.add_patch(wedge)

    r = 0.52
    x, y = r * np.cos(mid_angle), r * np.sin(mid_angle)
    ax.text(x, y, f"{metric['count']}\n{metric['pct']:.1f}%", ha='center', va='center',
           fontsize=12, weight='bold', color='black')

    start_angle += angle_size

circle_outer = Circle((0, 0), 0.40, facecolor=color_light, edgecolor='white', linewidth=2)
ax.add_patch(circle_outer)

ax.text(0, 0.05, 'N=16', ha='center', va='center', fontsize=14, weight='bold', color='black')
ax.text(0, -0.08, f'Total: {total}', ha='center', va='center', fontsize=12, weight='bold', color='black')

circle_center = Circle((0, 0), 0.20, facecolor='white', edgecolor='white', linewidth=0)
ax.add_patch(circle_center)

ax.set_xlim(-1.1, 1.1)
ax.set_ylim(-1.1, 1.1)
ax.axis('off')

plt.tight_layout()
plt.savefig('figure5_evaluation_metrics.png', dpi=300, bbox_inches='tight', facecolor='white')
plt.show()
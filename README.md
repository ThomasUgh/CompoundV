<div align="center">
<img src="https://i.ibb.co/fV94VT6P/compound-v.png" alt="Logo" width="250"/>

# 🧪 CompoundV

### *Inject the powers of* The Boys *into Minecraft.*

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Paper](https://img.shields.io/badge/Paper-F7E7CE?style=for-the-badge&logo=paper&logoColor=black)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-ED1C24?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)

</div>

---

> Most heroes don't get their powers in a lab.
> On your server, they will. **One bottle at a time.**

CompoundV is a modular ability plugin inspired by *The Boys*. Players drink experimental serums to roll for super powers — from flight and heat vision to a one-shot chest beam that vaporizes anyone in its path. Every ability has real controls, real cooldowns, and real consequences.

<div align="center">

🧪 **Drink. Pray. Survive.** 🧪

</div>

---

## 🧪 The Serums

<table>
<tr>
<th width="20%">Serum</th>
<th width="15%">Color</th>
<th width="65%">What it does</th>
</tr>
<tr>
<td align="center"><b>Compound&nbsp;V</b></td>
<td align="center">💙</td>
<td>The classic. Random ability, permanent until death. Heroes are extremely rare.</td>
</tr>
<tr>
<td align="center"><b>Temp&nbsp;V</b></td>
<td align="center">💚</td>
<td>A weaker, time-limited dose. Power fades after a configurable duration.</td>
</tr>
<tr>
<td align="center"><b>V&nbsp;One</b></td>
<td align="center">🩵</td>
<td>The refined formula. Only rolls top-tier abilities</b>.</td>
</tr>
<tr>
<td align="center"><b>Anti&nbsp;V</b></td>
<td align="center">❤️</td>
<td>The cure. Drink to remove your current ability.</td>
</tr>
</table>

> 🖱️ **Right-click** a bottle to drink. After a short activation, your power is unlocked.

---

## 🦸 The Abilities

> *Click any ability to expand its full description.*

<details>
<summary><b>🔴 The Patriot</b> — <i>like Homelander</i></summary>
<br>

The face of Vought. Untouchable, arrogant, absolutely lethal. **Compound V's rarest roll** — the **V One variant** is even stronger.

- ✈️ **Permanent flight** — automatic, no toggle
- 🔥 **Heat Vision** — press <kbd>F</kbd> to toggle a high-damage eye beam
- 👁️ **X-Ray** — <kbd>Sneak</kbd> + <kbd>Right-Click</kbd> to reveal nearby entities through walls
- 🚀 **Jump Start** — <kbd>Sneak</kbd>, look up, then <kbd>Jump</kbd> to launch yourself skyward
- 💥 **Sonic Boom Landing** — fall from 30+ blocks for a devastating impact explosion
- 💪 Permanent **Strength III**, **Resistance II**, **Regeneration** & **+10 bonus hearts**

> 🩸 *V One variant adds Strength IV, Resistance III, fire resistance, faster flight and a stronger Jump Start.*

</details>

<details>
<summary><b>🟡 The Veteran</b> — <i>like Soldier Boy</i></summary>
<br>

The original supe. Pissed off, off the grid, and packing nuclear-grade firepower in his chest. **V One exclusive.**

- 💪 Passive **Strength V** and **Resistance III**
- ☢️ **Chest Beam** — <kbd>Sneak</kbd> + <kbd>Right-Click</kbd> to fire a thick yellow energy beam for 3 seconds
  - Tears through blocks · ignites everything it touches · melts players and mobs
  - **Strips abilities from any supe it hits** — *yes, even another Patriot*
- 💥 **Ground Zero activation** — releases a massive visual shockwave on cast *(no terrain damage)*
- ⏳ 45 second cooldown — use it wisely

</details>

<details>
<summary><b>✈️ Flight</b></summary>
<br>

Simple, clean, permanent. Vanilla creative-style flight controls. No combat boosts — just the sky.

</details>

<details>
<summary><b>🔥 Heat Vision</b></summary>
<br>

Press <kbd>F</kbd> to fire a long-range laser from your eyes. Configurable damage, range and whether it ignites blocks/entities or breaks glass and leaves.

</details>

<details>
<summary><b>⚡ Speedster</b> — <i>like A-Train</i></summary>
<br>

Press <kbd>F</kbd> to toggle **Speed IV**. Small Resistance bonus while sprinting. Outrun anything that breathes.

</details>

<details>
<summary><b>💪 Super Strength</b></summary>
<br>

Permanent **Strength III** and a Resistance bonus. No toggle, no cooldown. You just hit harder. Forever.

</details>

<details>
<summary><b>👻 Invisibility</b></i></summary>
<br>

Press <kbd>F</kbd> to vanish (**Invisibility II**) with a Resistance bonus. Same control as Heat Vision — your ability, your stealth.

</details>

<details>
<summary><b>♨️ Fire</b></summary>
<br>

A passive ring of fire around you ignites enemies automatically. Comes with Fire Resistance. Walking through a village has never been more chaotic.

</details>

---

## 🎮 Controls Cheat Sheet

| Action | Key |
| :-- | :-: |
| Toggle Heat Vision / Speedster / Ghost Mode | <kbd>F</kbd> *(swap offhand)* |
| X-Ray *(The Patriot)* | <kbd>Sneak</kbd> + <kbd>Right-Click</kbd> |
| Jump Start *(The Patriot)* | <kbd>Sneak</kbd> + look up + <kbd>Jump</kbd> |
| Chest Beam *(The Veteran)* | <kbd>Sneak</kbd> + <kbd>Right-Click</kbd> |
| Drink a serum | <kbd>Right-Click</kbd> with bottle in hand |

> 🧪 Drink an **Anti V** to remove your current ability and become normal again.

---

## 📋 Commands & Permissions

<details>
<summary><b>📜 Commands</b></summary>
<br>

Main command: `/compoundv` — alias: `/cv`

| Command | Description |
| :-- | :-- |
| `/cv help` | Show the in-game help |
| `/cv info [player]` | View your own or another player's active ability |
| `/cv give <player> bottle <compoundv\|tempv\|vone\|antiv>` | Give a serum bottle |
| `/cv give <player> <ability> [potionType]` | Directly assign an ability |
| `/cv remove <player>` | Strip a player's ability |
| `/cv reload` | Reload config and language files |

</details>

<details>
<summary><b>🔑 Permissions</b></summary>
<br>

| Permission | Default | Description |
| :-- | :-: | :-- |
| `compoundv.use` | `true` | Drink serums and use abilities |
| `compoundv.admin` | `op` | Use `/cv give`, `/cv remove`, `/cv reload` |

</details>

---

## 🐛 Bug Reports & Suggestions
 
Found a bug or have an idea? Open an [issue on GitHub](https://github.com/thomasugh/compoundv/issues). Please include:
 
- Server software and version *(e.g. `Paper 1.21.4 v1.0.0`)*
- Plugin version
- A clear description and, if possible, console logs
---

## 📜 Credits

<div align="center">

Created by **Thomas U.**

Inspired by *The Boys* (Amazon Prime Video / Garth Ennis & Darick Robertson).
This is a **fan-made, non-commercial project** and is not affiliated with, endorsed by, or sponsored by
Amazon, Sony Pictures Television, or the rights holders of *The Boys*.
All trademarks belong to their respective owners.

All character archetype names in-game *(The Patriot, The Veteran)*
are original references — no trademarked names are used.

---

⭐ **If you enjoy the plugin, leave a star on GitHub!** ⭐

</div>

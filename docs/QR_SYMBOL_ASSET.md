# QR UI symbol

Source: user-supplied `19771.png`.
Website asset: `docs/pages/assets/images/qr-symbol.png`.
Processing: built-in imagegen edit with `transparent_background: true`.

The shared `i-qr` symbol uses the PNG alpha as an SVG mask and inherits
`currentColor`. This gives the same asset the appropriate theme color in
the provisioning card, Show QR button, and dialog heading.

## Editing prompt

Edit target: the supplied black QR-code-style UI symbol on white. Remove only the white background, including every white gap and every white hole inside the three square finder marks, replacing all of that white with genuine alpha transparency. Preserve the supplied mark's exact arrangement and silhouette: all three finder marks with black center squares, the two small center zigzag parts, every square fragment, and the lower-right fragments must retain exactly their current positions and proportions. No invented QR modules, no rearrangement, no redesign. Keep the mark solid black, flat and sharp, with no gradients, shadows, blur, text, stroke outlines, or extra objects. Tightly crop the square output canvas to the black symbol's outer bounds with just a very small transparent margin. This is a transparent UI icon asset, not a scannable provisioning QR. Return a PNG with an alpha channel.

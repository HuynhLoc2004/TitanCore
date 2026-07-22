# AI Rules

AI is allowed only outside realtime gameplay.

## Allowed AI Tasks

- Boss generation
- Monster generation
- Map generation
- Quest generation
- Skill generation
- Lore generation
- Item generation
- Reward generation
- Story generation
- Image prompt generation

## Required Controls

- AI returns JSON only.
- Backend validates all AI output.
- Cache prompts by hash.
- Cache responses.
- Store accepted content in PostgreSQL.
- Never regenerate existing content unnecessarily.
- Image generation is asynchronous and uploaded to Cloudinary.


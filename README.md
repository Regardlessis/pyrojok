# StunPop

A small Paper/Purpur plugin that adds a configurable upward pop after a real axe shield-disable and a follow-up hit.

## Build

Requires Java 21 and Maven.

```bash
mvn package
```

The plugin jar will be here:

```text
target/stunpop-1.0.0.jar
```

Upload that jar into `/plugins`, restart the server, and edit `/plugins/StunPop/config.yml`.

## Important Paper setting

Keep this enabled in `/config/paper-global.yml`:

```yaml
unsupported-settings:
  skip-vanilla-damage-tick-when-shield-blocked: true
```

## Useful config presets

For any follow-up hit:

```yaml
follow-up-weapon: ANY
vertical-velocity: 0.62
horizontal-velocity: 0.18
minimum-attacker-fall-distance: 0.0
```

For mace-only stun-slam style:

```yaml
follow-up-weapon: MACE
vertical-velocity: 0.70
horizontal-velocity: 0.12
minimum-attacker-fall-distance: 1.5
```

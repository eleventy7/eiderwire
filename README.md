# Eider Wire

## Wire protocol for efficient data transfer

### Header Layout

```
32 bit - message length
16 bit - encoding type (decimal 43; 0x002B)
16 bit - message protocol id
16 bit - version
```

## Requirements

- Java 21
- Gradle 8.5 
- Agrona 1.20.0

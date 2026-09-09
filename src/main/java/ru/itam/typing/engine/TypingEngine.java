package ru.itam.typing.engine;

import ru.itam.typing.model.AssetTypingContext;
import ru.itam.typing.model.TypingResult;

public interface TypingEngine {
    String name();
    TypingResult classify(AssetTypingContext context);
}

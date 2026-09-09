package ru.itam.typing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.itam.typing.data.DatasetGenerator;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DatasetGeneratorTest {
    @TempDir Path temp;

    @Test
    void fixedSeedProducesByteIdenticalDataset() throws Exception {
        Path a = temp.resolve("a.jsonl");
        Path b = temp.resolve("b.jsonl");
        var generator = new DatasetGenerator();
        generator.generate(250, 20260909L, a);
        generator.generate(250, 20260909L, b);
        assertArrayEquals(Files.readAllBytes(a), Files.readAllBytes(b));
    }
}

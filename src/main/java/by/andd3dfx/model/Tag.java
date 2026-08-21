package by.andd3dfx.model;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum Tag {
    DEFAULT,
    STYLE,
    SQL,
    DDL,
    BUG,
    TEST,
    UTIL,
    CLARIFY,
    CHECKSTYLE,
    APPREFACTOR,
    MRREFACTOR,
    PERF,
    DEFECT,
    MISSED,
    QUESTION;

    public static final List<Tag> TAGS = Arrays.stream(Tag.values()).filter(tag -> !DEFAULT.equals(tag)).collect(Collectors.toList());
}

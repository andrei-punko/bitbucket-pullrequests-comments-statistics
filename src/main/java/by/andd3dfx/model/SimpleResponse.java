package by.andd3dfx.model;

import java.util.List;

public record SimpleResponse<T>(List<T> values, String next) {
}

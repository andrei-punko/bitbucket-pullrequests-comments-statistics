package by.andd3dfx.model;

import java.util.List;

public record Response<T>(List<T> values, Long size, Long page, String next) {
}

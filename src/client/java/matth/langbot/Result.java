package matth.langbot;

public sealed interface Result<T> {
    record Ok<T>(T t) implements Result<T> {}
    record Loading<T>() implements Result<T> {}
    record Error<T>(String e) implements Result<T> {}
}
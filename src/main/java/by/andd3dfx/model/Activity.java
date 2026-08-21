package by.andd3dfx.model;

public record Activity(Update update, DateHolder approval) {

    public record DateHolder(String date) {
    }

    public record Update(String date, Source source) {
    }

    public record Source(Commit commit) {
    }

    public record Commit(String hash) {
    }

    public String getDate() {
        if (this.approval != null) {
            return this.approval.date();
        } else {
            if (this.update != null) {
                return this.update.date();
            }
        }
        return null;
    }
}

package by.andd3dfx.service;

import com.opencsv.CSVWriter;
import by.andd3dfx.model.Comment;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class CsvExport {
    public void writeDataToCsv(String filePath, List<Comment> data) {
        try (CSVWriter writer = new CSVWriter(new FileWriter(filePath))) {
            for (Comment comment : data) {
                String[] entries = new String[]{
                        comment.id().toString(),
                        comment.content().raw()
                };
                writer.writeNext(entries);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

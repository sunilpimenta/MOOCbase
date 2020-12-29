package edu.berkeley.cs186.database.cli;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import edu.berkeley.cs186.database.Database;
import edu.berkeley.cs186.database.DatabaseException;
import edu.berkeley.cs186.database.Transaction;
import edu.berkeley.cs186.database.concurrency.LockManager;
import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.databox.FloatDataBox;
import edu.berkeley.cs186.database.databox.IntDataBox;
import edu.berkeley.cs186.database.databox.StringDataBox;
import edu.berkeley.cs186.database.databox.Type;
import edu.berkeley.cs186.database.memory.ClockEvictionPolicy;
import edu.berkeley.cs186.database.table.Schema;

public class ExampleLoader {
    public static Database setupDatabase() throws IOException {
        // Basic database for project 1 through 3
        Database database = new Database("demo", 25);
        
        // Use the following after completing project 4 (locking)
        // Database database = new Database("demo", 25, new LockManager());
        
        // Use the following after completing project 5 (recovery)
        // Database database = new Database("demo", 25, new LockManager(), new ClockEvictionPolicy(), true);
        database.setWorkMem(5); // B=5

        //Create schemas

        List<String> studentSchemaNames = new ArrayList<>();
        studentSchemaNames.add("sid");
        studentSchemaNames.add("name");
        studentSchemaNames.add("major");
        studentSchemaNames.add("gpa");

        List<Type> studentSchemaTypes = new ArrayList<>();
        studentSchemaTypes.add(Type.intType());
        studentSchemaTypes.add(Type.stringType(20));
        studentSchemaTypes.add(Type.stringType(20));
        studentSchemaTypes.add(Type.floatType());

        Schema studentSchema = new Schema(studentSchemaNames, studentSchemaTypes);

        try(Transaction t = database.beginTransaction()) {
            try {
                t.createTable(studentSchema, "Students");
            } catch (DatabaseException e) {
                return database;
            }

            List<String> courseSchemaNames = new ArrayList<>();
            courseSchemaNames.add("cid");
            courseSchemaNames.add("name");
            courseSchemaNames.add("department");

            List<Type> courseSchemaTypes = new ArrayList<>();
            courseSchemaTypes.add(Type.intType());
            courseSchemaTypes.add(Type.stringType(20));
            courseSchemaTypes.add(Type.stringType(20));

            Schema courseSchema = new Schema(courseSchemaNames, courseSchemaTypes);
            try {
                t.createTable(courseSchema, "Courses");
            } catch (DatabaseException e) {
                // Do nothing
            }

            List<String> enrollmentSchemaNames = new ArrayList<>();
            enrollmentSchemaNames.add("sid");
            enrollmentSchemaNames.add("cid");

            List<Type> enrollmentSchemaTypes = new ArrayList<>();
            enrollmentSchemaTypes.add(Type.intType());
            enrollmentSchemaTypes.add(Type.intType());

            Schema enrollmentSchema = new Schema(enrollmentSchemaNames, enrollmentSchemaTypes);
            try {
                t.createTable(enrollmentSchema, "Enrollments");
            } catch (DatabaseException e) {
                // Do nothing
            }
        }

        try(Transaction transaction = database.beginTransaction()) {
            // read student tuples
            List<String> studentLines = Files.readAllLines(Paths.get("data", "Students.csv"), Charset.defaultCharset());

            for (String line : studentLines) {
                String[] splits = line.split(",");
                List<DataBox> values = new ArrayList<>();

                values.add(new IntDataBox(Integer.parseInt(splits[0])));
                values.add(new StringDataBox(splits[1].trim(), 20));
                values.add(new StringDataBox(splits[2].trim(), 20));
                values.add(new FloatDataBox(Float.parseFloat(splits[3])));

                transaction.insert("Students", values);
            }

            List<String> courseLines = Files.readAllLines(Paths.get("data", "Courses.csv"), Charset.defaultCharset());

            for (String line : courseLines) {
                String[] splits = line.split(",");
                List<DataBox> values = new ArrayList<>();

                values.add(new IntDataBox(Integer.parseInt(splits[0])));
                values.add(new StringDataBox(splits[1].trim(), 20));
                values.add(new StringDataBox(splits[2].trim(), 20));

                transaction.insert("Courses", values);
            }

            List<String> enrollmentLines = Files.readAllLines(Paths.get("data", "Enrollments.csv"),
                                        Charset.defaultCharset());

            for (String line : enrollmentLines) {
                String[] splits = line.split(",");
                List<DataBox> values = new ArrayList<>();

                values.add(new IntDataBox(Integer.parseInt(splits[0])));
                values.add(new IntDataBox(Integer.parseInt(splits[1])));

                transaction.insert("Enrollments", values);
            }
        }
        return database;
    }
}
package com.codingapi.report.service;

import com.codingapi.report.context.JdbcTemplateContext;
import com.codingapi.report.data.DataResult;
import com.codingapi.report.entity.Student;
import com.codingapi.report.meta.DBTable;
import com.codingapi.report.repository.StudentRepository;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class DataServiceTest {

    @Autowired
    private DataService dataService;
    @Autowired
    private StudentRepository studentRepository;


    @Test
    void test() {
        dataService.clear();

        dataService.init();

        dataService.test("一年级一模考试", "2026-03-23");
        dataService.test("一年级二模考试", "2026-04-23");
        dataService.test("一年级三模考试", "2026-05-23");

    }


    @Test
    void age() {

        List<Student> students = studentRepository.findAll();
        assertEquals(4, students.size());

        for (Student student : students) {
            System.out.println(student.getAge());
        }
    }

    @SneakyThrows
    @Test
    void queryScore() {
        String sql = """
                select ss.id,stu.id as student_id,stu.name as student_name,sc.name as class_name,sub.name as subject_name, ss.score from t_student_score ss join t_student_test st on st.id = ss.test_id JOIN t_student stu on stu.id = ss.student_id join t_subject sub on ss.subject_id = sub.id join t_student_class sc on stu.class_id = sc.id where st.title = ?;
                """;
        String title = "一年级一模考试";
        DataResult result = JdbcTemplateContext.getInstance().queryForList(sql, title);
        JdbcTemplateContext.getInstance().scannerMeta(null);
        assertEquals(4 * 3, result.size());
    }
}
package com.codingapi.report.service;

import com.codingapi.report.entity.*;
import com.codingapi.report.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
@AllArgsConstructor
public class DataService {

    private final StudentRepository studentRepository;
    private final StudentClassRepository studentClassRepository;
    private final StudentScoreRepository studentScoreRepository;
    private final StudentTestRepository studentTestRepository;
    private final SubjectRepository subjectRepository;


    public void clear() {
        studentRepository.deleteAll();
        studentClassRepository.deleteAll();
        studentScoreRepository.deleteAll();
        studentTestRepository.deleteAll();
        subjectRepository.deleteAll();
    }


    public void init() {
        this.initStudentClasses();
        this.initSubjects();
        this.initStudents();
    }


    private void initStudentClasses() {
        studentClassRepository.save(new StudentClass(1, "一年级一班"));
        studentClassRepository.save(new StudentClass(2, "一年级二班"));
    }


    private void initSubjects() {
        subjectRepository.save(new Subject(1, "语文"));
        subjectRepository.save(new Subject(2, "数学"));
        subjectRepository.save(new Subject(3, "英语"));
    }


    private void initStudents() {
        studentRepository.save(new Student(1, "张三", "2017-12-07", 0, 1));
        studentRepository.save(new Student(2, "李四", "2018-12-07", 1, 2));
        studentRepository.save(new Student(3, "王五", "2017-02-17", 1, 1));
        studentRepository.save(new Student(4, "赵六", "2017-09-22", 0, 2));
    }


    public void test(String title, String date) {
        StudentTest test = new StudentTest(title, date);
        studentTestRepository.save(test);

        List<Subject> subjects = this.subjectRepository.findAll();
        List<Student> students = this.studentRepository.findAll();

        Random random = new Random();

        for (Student student : students) {
            for (Subject subject : subjects) {
                int score = random.nextInt(100);
                StudentScore studentScore = new StudentScore(student.getId(), test.getId(), subject.getId(), score);
                this.studentScoreRepository.save(studentScore);
            }
        }
    }

}

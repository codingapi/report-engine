package com.codingapi.report.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * 考试
 */
@Data
@Entity
@Table(name = "t_student_test")
@Comment("考试信息")
@NoArgsConstructor
public class StudentTest {

    @Id
    @GeneratedValue
    @Comment("编号")
    private long id;

    @Comment("标题")
    private String title;

    @Comment("考试日期")
    private String date;


    public StudentTest(String title, String date) {
        this.title = title;
        this.date = date;
    }
}

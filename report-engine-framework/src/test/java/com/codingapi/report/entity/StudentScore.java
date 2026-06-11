package com.codingapi.report.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * 学生成绩
 */
@Data
@Entity
@Table(name = "t_student_score")
@Comment("学生成绩")
@NoArgsConstructor
public class StudentScore {

    @Id
    @GeneratedValue
    @Comment("编号")
    private long id;

    @Comment("学生编号")
    private long studentId;

    @Comment("考试编号")
    private long testId;

    @Comment("科目编号")
    private long subjectId;

    @Comment("成绩")
    private float score;

    public StudentScore(long studentId, long testId, long subjectId, float score) {
        this.studentId = studentId;
        this.testId = testId;
        this.subjectId = subjectId;
        this.score = score;
    }
}

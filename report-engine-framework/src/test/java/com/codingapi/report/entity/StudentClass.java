package com.codingapi.report.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * 班级信息
 */
@Data
@Entity
@Table(name = "t_student_class")
@Comment("班级信息")
@NoArgsConstructor
@AllArgsConstructor
public class StudentClass {

    @Id
    @Comment("编号")
    private long id;

    @Comment("名称")
    private String name;

}

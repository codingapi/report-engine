package com.codingapi.report.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Data
@Entity
@Table(name = "t_subject")
@Comment("科目信息")
@NoArgsConstructor
@AllArgsConstructor
public class Subject {

    @Id
    @Comment("编号")
    private long id;

    @Comment("名称")
    private String name;

}

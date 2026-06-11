package com.codingapi.report.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.hibernate.annotations.Comment;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

@Data
@Entity
@Table(name = "t_student")
@Comment("学生信息")
@NoArgsConstructor
@AllArgsConstructor
public class Student {

    private final static DateFormat format = new SimpleDateFormat("yyyy-MM-dd");

    @Id
    @Comment("编号")
    private long id;

    @Comment("姓名")
    private String name;

    @Comment("生日")
    private String birthday;

    @Comment("性别")
    private int sex;

    @Comment("班级编号")
    private long classId;


    @SneakyThrows
    public int getAge(){
        // 1. 解析生日字符串为 Date
        Date birthday = format.parse(this.birthday);

        // 2. 获取当前日期
        Calendar today = Calendar.getInstance();
        Calendar birth = Calendar.getInstance();
        birth.setTime(birthday);

        // 3. 计算年龄差值
        int age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR);

        // 4. 如果今年生日还没过，年龄减 1
        // 比较“月-日”：当前月小于出生月；或者同月但当前日小于出生日
        if (today.get(Calendar.MONTH) < birth.get(Calendar.MONTH)) {
            age--;
        } else if (today.get(Calendar.MONTH) == birth.get(Calendar.MONTH)
                && today.get(Calendar.DAY_OF_MONTH) < birth.get(Calendar.DAY_OF_MONTH)) {
            age--;
        }
        return age;
    }


}

package mini.student.quiz.model;

public class User {

    private String id;
    private String name;
    private String email;
    private String avatar;
    private String idgv;

    public User() {
    }

    public User(String id, String name, String email, String avatar, String idgv) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.avatar = avatar;
        this.idgv = idgv;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getIdgv() {
        return idgv;
    }

    public void setIdgv(String idgv) {
        this.idgv = idgv;
    }
}

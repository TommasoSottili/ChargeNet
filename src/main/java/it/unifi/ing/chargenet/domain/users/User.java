package it.unifi.ing.chargenet.domain.users;

public  abstract class User {

    private String name;
    private String password;
    private String email;
    private Long id;
    private Role role;

    protected User() {};
    public User(String name, String password, String email, Role role) {
        this.name = name;
        this.password = password;
        this.email = email;
        this.role = role;
    }
    public long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public Role getRole() {
        return role;
    }
    private  void setRole(Role role) { // scelta di protected: il ruolo di un utente viene assegnato alla sua
        this.role = role;              // creazione e non dovrebbe poter essere modificato liberamente da
    }                                  // qualsiasi altra classe esterna


}

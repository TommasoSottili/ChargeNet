package it.unifi.ing.chargenet.domain.users;

public class EnergyManager extends User {

    protected EnergyManager() {
        super();
    }
    public EnergyManager(String name, String password, String email) {
        super(name, email, password, Role.ENERGY_MANAGER);
    }
    // Metodo statico per ricostruire l'oggetto dal Database
    public static EnergyManager reconstitute(Long id, String name, String email, String password) {

        EnergyManager manager = new EnergyManager();

        // Campi ereditati da User
        manager.setId(id);
        manager.setName(name);
        manager.setEmail(email);
        manager.setPassword(password);
        manager.setRole(Role.ENERGY_MANAGER);

        return manager;
    }
}

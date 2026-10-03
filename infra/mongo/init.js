// Crea un usuario por base, con permisos solo sobre la suya.
// Corre una sola vez: cuando el volumen mongodata esta vacio.
const bases = [
  { nombre: 'classification_db', user: 'classification_user', pwd: process.env.CLASSIFICATION_DB_PASSWORD },
  { nombre: 'notifications_db', user: 'notifications_user', pwd: process.env.NOTIFICATIONS_DB_PASSWORD },
  { nombre: 'reporting_db', user: 'reporting_user', pwd: process.env.REPORTING_DB_PASSWORD },
];

for (const { nombre, user, pwd } of bases) {
  if (!pwd) throw new Error(`Falta la contrasena de ${user} en el .env`);
  db.getSiblingDB(nombre).createUser({ user, pwd, roles: [{ role: 'readWrite', db: nombre }] });
}
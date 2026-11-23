package jake2.qcommon

/** Reports a rejected collision query or inconsistent state in an active collision world. */
class CollisionStateException(message: String) : IllegalStateException(message)

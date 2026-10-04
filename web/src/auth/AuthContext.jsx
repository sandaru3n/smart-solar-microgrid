import { createContext, useContext, useState } from 'react';
import { authApi } from '../api';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(() => {
    const storedUser = localStorage.getItem('user') || sessionStorage.getItem('user');
    return storedUser ? JSON.parse(storedUser) : null;
  });

  const login = async (nic, password, rememberMe = false) => {
    const data = await authApi.login({ nic, password });
    
    if (data.role === 'PROSUMER') {
      throw new Error('Prosumers must use the Mobile App to log in.');
    }
    
    // Check if account is not active (though backend may block it too, we should handle it if passed)
    if (data.accountStatus === 'PENDING') {
      throw new Error('Your account is pending activation.');
    }
    if (data.accountStatus === 'DEACTIVATED') {
      throw new Error('Your account has been deactivated.');
    }

    const userData = {
      token: data.token,
      nic: data.nic,
      name: data.name,
      role: data.role,
      accountStatus: data.accountStatus,
    };
    
    if (rememberMe) {
      localStorage.setItem('token', data.token);
      localStorage.setItem('user', JSON.stringify(userData));
    } else {
      sessionStorage.setItem('token', data.token);
      sessionStorage.setItem('user', JSON.stringify(userData));
    }
    setUser(userData);
    return userData;
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    sessionStorage.removeItem('token');
    sessionStorage.removeItem('user');
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = () => useContext(AuthContext);

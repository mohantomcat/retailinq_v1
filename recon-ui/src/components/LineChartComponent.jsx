import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts";
import { Typography, Box } from "@mui/material";

export default function LineChartComponent({ data, dataKey, title }) {
  return (
    <Box sx={{ bgcolor: "white", p: 2, borderRadius: 2, height: 300 }}>
      <Typography variant="subtitle1" sx={{ mb: 1 }}>{title}</Typography>
      <ResponsiveContainer width="100%" height="85%">
        <LineChart data={data}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="date" />
          <YAxis />
          <Tooltip />
          <Line type="monotone" dataKey={dataKey} stroke="#1976d2" strokeWidth={2} />
        </LineChart>
      </ResponsiveContainer>
    </Box>
  );
}
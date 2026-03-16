import {LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer} from "recharts";
import {Typography, Box} from "@mui/material";

export default function LineChartComponent({
  data,
  dataKey,
  title,
  xKey = "date",
  emptyMessage = "No trend data available for the selected range.",
}) {
  const hasData = Array.isArray(data) && data.length > 0;

  return (
    <Box sx={{bgcolor: "white", p: 2, borderRadius: 2, height: 300}}>
      <Typography variant="subtitle1" sx={{mb: 1}}>{title}</Typography>
      {hasData ? (
        <ResponsiveContainer width="100%" height="85%">
          <LineChart data={data} margin={{top: 8, right: 16, left: 0, bottom: 8}}>
            <CartesianGrid strokeDasharray="3 3"/>
            <XAxis dataKey={xKey}/>
            <YAxis allowDecimals={false}/>
            <Tooltip/>
            <Line
              type="monotone"
              dataKey={dataKey}
              stroke="#1976d2"
              strokeWidth={2}
              dot={{r: 3}}
              activeDot={{r: 5}}
              connectNulls
            />
          </LineChart>
        </ResponsiveContainer>
      ) : (
        <Box sx={{height: "85%", display: "flex", alignItems: "center", justifyContent: "center"}}>
          <Typography variant="body2" sx={{color: "#64748b"}}>
            {emptyMessage}
          </Typography>
        </Box>
      )}
    </Box>
  );
}
